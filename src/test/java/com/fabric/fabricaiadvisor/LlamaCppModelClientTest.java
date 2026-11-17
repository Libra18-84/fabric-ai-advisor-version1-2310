package com.fabric.fabricaiadvisor;

import com.fabric.fabricaiadvisor.service.LlamaCppModelClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import org.springframework.http.*;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.*;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class LlamaCppModelClientTest {
    private final ObjectMapper json = new ObjectMapper();
    private record Bound(LlamaCppModelClient client, MockRestServiceServer server) {}
    private Bound bound() {
        var builder = RestClient.builder().baseUrl("http://llama.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        return new Bound(new LlamaCppModelClient(builder.build(), "gemma-main"), server);
    }

    @Test void usesOpenAiMessagesAndJsonResponseFormat() {
        var b = bound();
        b.server.expect(requestTo("http://llama.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(request -> {
                    var body = json.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertEquals("gemma-main", body.path("model").asText());
                    assertEquals("system", body.path("messages").get(0).path("role").asText());
                    assertEquals("Chỉ dùng nguồn", body.path("messages").get(0).path("content").asText());
                    assertEquals("user", body.path("messages").get(1).path("role").asText());
                    assertEquals("Sơ mi", body.path("messages").get(1).path("content").asText());
                    assertEquals("json_object", body.path("response_format").path("type").asText());
                    assertFalse(body.path("stream").asBoolean());
                    assertEquals(0, body.path("temperature").asInt());
                    assertEquals(2048, body.path("max_tokens").asInt());
                }).andRespond(withSuccess(json.writeValueAsString(Map.of("choices", List.of(
                        Map.of("finish_reason", "stop", "message", Map.of("content", "  {\"ok\":true}  "))))),
                        MediaType.APPLICATION_JSON));
        assertEquals("{\"ok\":true}", b.client.generateJson("Chỉ dùng nguồn", "Sơ mi"));
        b.server.verify();
    }

    @Test void validatesHealthAndExactModelAlias() {
        var b = bound();
        b.server.expect(requestTo("http://llama.test/health"))
                .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));
        b.server.expect(requestTo("http://llama.test/v1/models"))
                .andRespond(withSuccess("{\"data\":[{\"id\":\"gemma-main\"}]}", MediaType.APPLICATION_JSON));
        assertDoesNotThrow(b.client::checkAvailability);
        b.server.verify();
    }

    @Test void rejectsWrongAliasEvenWhenServerIsHealthy() {
        var b = bound();
        b.server.expect(requestTo("http://llama.test/health"))
                .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));
        b.server.expect(requestTo("http://llama.test/v1/models"))
                .andRespond(withSuccess("{\"data\":[{\"id\":\"another-model\"}]}", MediaType.APPLICATION_JSON));
        assertThrows(IllegalStateException.class, b.client::checkAvailability);
        b.server.verify();
    }

    @Test void loadingServerIsUnavailable() {
        var b = bound();
        b.server.expect(requestTo("http://llama.test/health"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(RestClientException.class, b.client::checkAvailability);
        b.server.verify();
    }

    @Test void unhealthyStatusDoesNotProbeModels() {
        var b = bound();
        b.server.expect(requestTo("http://llama.test/health"))
                .andRespond(withSuccess("{\"status\":\"loading\"}", MediaType.APPLICATION_JSON));
        assertThrows(IllegalStateException.class, b.client::checkAvailability);
        b.server.verify();
    }

    @TestFactory Stream<DynamicTest> malformedOrUnfinishedResponsesAreRejected() {
        return Stream.of(
                "{}", "{\"choices\":[]}",
                "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{}\"}}]}",
                "{\"choices\":[{\"finish_reason\":\"content_filter\",\"message\":{\"content\":\"{}\"}}]}",
                "{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}",
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\" \"}}]}",
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":null}}]}",
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{}\",\"refusal\":\"refused\"}}]}",
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":[{}]}}]}"
        ).map(response -> DynamicTest.dynamicTest(response, () -> {
            var b = bound();
            b.server.expect(requestTo("http://llama.test/v1/chat/completions"))
                    .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
            assertThrows(IllegalStateException.class, () -> b.client.generateJson("system", "question"));
            b.server.verify();
        }));
    }

    @Test void transportFailureRemainsDistinctFromMalformedContent() {
        var b = bound();
        b.server.expect(requestTo("http://llama.test/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(RestClientException.class, () -> b.client.generateJson("system", "question"));
        b.server.verify();
    }

    @Test void invalidInputsDoNotSendHttpRequests() {
        var b = bound();
        assertThrows(IllegalArgumentException.class, () -> b.client.generateJson("", "question"));
        assertThrows(IllegalArgumentException.class, () -> b.client.generateJson("system", null));
        assertThrows(IllegalArgumentException.class, () -> new LlamaCppModelClient(RestClient.create(), " "));
        assertThrows(IllegalArgumentException.class, () -> new LlamaCppModelClient("http://localhost", "main", Duration.ZERO));
    }
}
