package com.fabric.fabricaiadvisor.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class AiModelRegistryTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong clock = new AtomicLong(1);
    private final Set<String> unavailable = new HashSet<>();
    private Path file;
    private AiModelRegistry registry;

    private final class Stub implements AiModelClient {
        private final String model;
        Stub(String model) { this.model = model; }
        public String generateJson(String system, String input) { return "{}"; }
        public String getModelName() { return model; }
        public void checkAvailability() {
            if (unavailable.contains(model)) throw new IllegalStateException("Model unavailable");
        }
    }

    @BeforeEach void setup() {
        file = directory.resolve("models.json");
        var defaults = new AiModelRegistry.Selection("environment", "env", null, Map.of("env", new Stub("old")));
        Function<AiModelRegistry.Spec, AiModelClient> factory = spec -> new Stub(spec.model());
        registry = new AiModelRegistry(mapper, file, defaults, factory, clock::get);
    }

    private Map<String, Object> profile(String name) {
        return Map.of("provider", "ollama", "endpoint", "http://localhost:11434", "model", name, "readTimeoutSeconds", 60);
    }

    private void write(String revision, String main, String evaluation) throws Exception {
        var active = new LinkedHashMap<String, Object>();
        active.put("main", main); active.put("evaluation", evaluation);
        var data = Map.of("revision", revision, "active", active,
                "models", Map.of("a", profile("model-a"), "b", profile("model-b"), "e", profile("model-e")));
        Files.writeString(file, mapper.writeValueAsString(data));
        clock.addAndGet(2_000_000_000L);
    }

    @Test void loadsBothRolesFromFile() throws Exception {
        write("v1", "a", "e");
        var selected = registry.snapshot();
        assertEquals("v1", selected.revision());
        assertEquals("model-a", selected.main().getModelName());
        assertEquals("model-e", selected.evaluation().getModelName());
        assertTrue(selected.clients().containsKey("b"));
    }

    @Test void swapsWithoutReconstructingRegistry() throws Exception {
        write("v1", "a", "e"); registry.snapshot();
        write("v2", "b", "a");
        assertEquals("model-b", registry.snapshot().main().getModelName());
        assertEquals("model-a", registry.snapshot().evaluation().getModelName());
    }

    @Test void malformedJsonKeepsOldSelection() throws Exception {
        write("v1", "a", "e"); var old = registry.snapshot();
        Files.writeString(file, "{"); clock.addAndGet(2_000_000_000L);
        assertSame(old, registry.snapshot());
    }

    @Test void unknownIdKeepsOldSelection() throws Exception {
        write("v1", "a", "e"); var old = registry.snapshot();
        write("v2", "missing", "e");
        assertSame(old, registry.snapshot());
    }

    @Test void unsupportedProviderKeepsOldSelection() throws Exception {
        write("v1", "a", "e"); var old = registry.snapshot();
        Files.writeString(file, Files.readString(file).replace("ollama", "unsupported"));
        clock.addAndGet(2_000_000_000L);
        assertSame(old, registry.snapshot());
    }

    @Test void unhealthyEvaluatorDoesNotPartiallySwapMain() throws Exception {
        write("v1", "a", "e"); var old = registry.snapshot();
        unavailable.add("model-a"); write("v2", "b", "a");
        assertSame(old, registry.snapshot());
        assertEquals("model-a", registry.snapshot().main().getModelName());
    }

    @Test void retriesSameConfigAfterModelBecomesAvailable() throws Exception {
        write("v1", "a", "e"); registry.snapshot();
        unavailable.add("model-b"); write("v2", "b", "e");
        assertEquals("v1", registry.snapshot().revision());
        unavailable.clear(); clock.addAndGet(11_000_000_000L);
        assertEquals("v2", registry.snapshot().revision());
    }

    @Test void explicitlyDisablesEvaluator() throws Exception {
        write("v1", "a", null);
        assertNull(registry.snapshot().evaluation());
    }

    @Test void inFlightScopeKeepsOldPairDuringConcurrentReload() throws Exception {
        write("v1", "a", "e"); registry.snapshot();
        try (var scope = registry.openScope()) {
            write("v2", "b", "a");
            assertEquals("v2", CompletableFuture.supplyAsync(() -> registry.snapshot().revision()).get());
            assertEquals("v1", registry.snapshot().revision());
            assertEquals("model-a", registry.snapshot().main().getModelName());
            assertEquals("model-e", registry.snapshot().evaluation().getModelName());
        }
        assertEquals("v2", registry.snapshot().revision());
    }

    @Test void rejectsUnknownConfigurationFields() throws Exception {
        write("v1", "a", "e"); var old = registry.snapshot();
        Files.writeString(file, Files.readString(file).replace("\"revision\":", "\"typo\":true,\"revision\":"));
        clock.addAndGet(2_000_000_000L);
        assertSame(old, registry.snapshot());
    }

    @Test void rejectsWrongTimeoutType() throws Exception {
        write("v1", "a", "e"); var old = registry.snapshot();
        Files.writeString(file, Files.readString(file).replace("\"readTimeoutSeconds\":60", "\"readTimeoutSeconds\":\"60\""));
        clock.addAndGet(2_000_000_000L);
        assertSame(old, registry.snapshot());
    }

    @Test void missingFileKeepsLastGoodSelection() throws Exception {
        write("v1", "a", "e"); var old = registry.snapshot();
        Files.delete(file); clock.addAndGet(2_000_000_000L);
        assertSame(old, registry.snapshot());
    }
    @Test void worksWithNegativeMonotonicClockOrigin() throws Exception {
        clock.set(-100_000_000_000L);
        write("v1", "a", "e");
        assertEquals("v1", registry.snapshot().revision());
        write("v2", "b", "e");
        assertEquals("v2", registry.snapshot().revision());
    }
}
