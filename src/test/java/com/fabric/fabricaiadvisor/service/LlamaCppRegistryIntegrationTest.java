package com.fabric.fabricaiadvisor.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LlamaCppRegistryIntegrationTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private String endpoint;
    private final List<String> generated = new java.util.concurrent.CopyOnWriteArrayList<>();

    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            Object response;
            if (path.equals("/health")) response = Map.of("status", "ok");
            else if (path.equals("/v1/models")) response = Map.of("data", List.of(
                    Map.of("id", "main-a"), Map.of("id", "main-b"), Map.of("id", "eval-a")));
            else if (path.equals("/v1/chat/completions")) {
                var body = mapper.readTree(exchange.getRequestBody());
                String name = body.path("model").asText();
                generated.add(name);
                response = Map.of("choices", List.of(Map.of("finish_reason", "stop",
                        "message", Map.of("content", mapper.writeValueAsString(Map.of("selectedModel", name))))));
            } else {
                exchange.sendResponseHeaders(404, -1); exchange.close(); return;
            }
            byte[] bytes = mapper.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start(); endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
    }
    @AfterEach void close() { server.stop(0); }

    private Map<String, Object> profile(String model) {
        return Map.of("provider", "llamacpp", "endpoint", endpoint, "model", model, "readTimeoutSeconds", 60);
    }
    private void write(Path file, String revision, String main, String eval) throws Exception {
        Files.writeString(file, mapper.writeValueAsString(Map.of("revision", revision,
                "active", Map.of("main", "main", "evaluation", "eval"),
                "models", Map.of("main", profile(main), "eval", profile(eval)))));
    }
    private AiModelRegistry registry(Path file) {
        return new AiModelRegistry(mapper, file == null ? "" : file.toString(), "llamacpp", "llamacpp",
                endpoint, "main-a", endpoint, "eval-a");
    }

    @Test void fileConfigurationSelectsLlamaCppForBothRoles() throws Exception {
        Path file = directory.resolve("models.json"); write(file, "llamacpp-v1", "main-a", "eval-a");
        var selected = registry(file).snapshot();
        assertEquals("llamacpp-v1", selected.revision());
        assertInstanceOf(LlamaCppModelClient.class, selected.main());
        assertInstanceOf(LlamaCppModelClient.class, selected.evaluation());
        assertEquals("main-a", mapper.readTree(selected.main().generateJson("system", "query")).path("selectedModel").asText());
        assertEquals("eval-a", mapper.readTree(selected.evaluation().generateJson("system", "query")).path("selectedModel").asText());
        assertEquals(List.of("main-a", "eval-a"), generated);
    }

    @Test void environmentFallbackUsesLlamaCppWithoutAnyOllamaRequests() {
        var selected = registry(null).snapshot();
        assertEquals("environment", selected.revision());
        assertInstanceOf(LlamaCppModelClient.class, selected.main());
        assertInstanceOf(LlamaCppModelClient.class, selected.evaluation());
        assertDoesNotThrow(() -> selected.main().generateJson("system", "query"));
        assertEquals(List.of("main-a"), generated);
    }

    @Test void wrongEvaluationAliasKeepsThePreviousPair() throws Exception {
        Path file = directory.resolve("models.json"); write(file, "v1", "main-a", "eval-a");
        var registry = registry(file); var old = registry.snapshot();
        write(file, "v2", "main-b", "missing"); Thread.sleep(1100);
        assertSame(old, registry.snapshot());
        assertEquals("main-a", registry.snapshot().main().getModelName());
    }

    @Test void hotSwapKeepsInFlightRequestOnItsOriginalPair() throws Exception {
        Path file = directory.resolve("models.json"); write(file, "v1", "main-a", "eval-a");
        var registry = registry(file);
        try (var scope = registry.openScope()) {
            write(file, "v2", "main-b", "eval-a"); Thread.sleep(1100);
            assertEquals("v2", java.util.concurrent.CompletableFuture.supplyAsync(() -> registry.snapshot().revision()).get());
            assertEquals("main-a", registry.snapshot().main().getModelName());
        }
        assertEquals("main-b", registry.snapshot().main().getModelName());
    }
}
