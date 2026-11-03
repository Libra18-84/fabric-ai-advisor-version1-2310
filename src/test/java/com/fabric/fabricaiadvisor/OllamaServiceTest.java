
package com.fabric.fabricaiadvisor;

import com.fabric.fabricaiadvisor.service.OllamaService;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class OllamaServiceTest {

    private final OllamaService ollamaService =
            new OllamaService();

    // Ba bài kiểm thử đầu vào đã có.

    @Test
    void testRejectNullQuestion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ollamaService.generateAnswer(null)
        );
    }

    @Test
    void testRejectEmptyQuestion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ollamaService.generateAnswer("")
        );
    }

    @Test
    void testRejectBlankQuestion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ollamaService.generateAnswer("   ")
        );
    }

    // Tạo RestClient giả lập, không gọi Ollama thật.
    private OllamaService createMockService(
            String responseBody,
            HttpStatus status
    ) {
        RestClient restClient = RestClient.builder()
                .requestFactory((uri, method) ->
                        new org.springframework.http.client.ClientHttpRequest() {

                            @Override
                            public org.springframework.http.HttpMethod getMethod() {
                                return method;
                            }

                            @Override
                            public java.net.URI getURI() {
                                return uri;
                            }

                            private final java.io.ByteArrayOutputStream output =
                                    new java.io.ByteArrayOutputStream();

                            @Override
                            public org.springframework.http.HttpHeaders getHeaders() {
                                return new org.springframework.http.HttpHeaders();
                            }

                            @Override
                            public java.io.OutputStream getBody() {
                                return output;
                            }

                            @Override
                            public java.util.Map<String, Object> getAttributes() {
                                return new java.util.HashMap<>();
                            }

                            @Override
                            public org.springframework.http.client.ClientHttpResponse execute() {

                                MockClientHttpResponse response =
                                        new MockClientHttpResponse(
                                                responseBody.getBytes(StandardCharsets.UTF_8),
                                                status
                                        );

                                response.getHeaders().setContentType(
                                        MediaType.APPLICATION_JSON
                                );

                                return response;
                            }
                        }
                )
                .baseUrl("http://localhost:11434")
                .build();

        return new OllamaService(restClient);
    }

    @Test
    void testSuccessfulResponse() {
        OllamaService service = createMockService(
                "{\"response\":\"Vải cotton phù hợp để may áo sơ mi.\"}",
                HttpStatus.OK
        );

        String result = service.generateAnswer(
                "Vải cotton có phù hợp không?"
        );

        assertEquals(
                "Vải cotton phù hợp để may áo sơ mi.",
                result
        );
    }

    @Test
    void testEmptyResponseBody() {
        OllamaService service = createMockService(
                "{}",
                HttpStatus.OK
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.generateAnswer("Tư vấn vải cotton")
        );
    }

    @Test
    void testBlankAnswer() {
        OllamaService service = createMockService(
                "{\"response\":\"   \"}",
                HttpStatus.OK
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.generateAnswer("Tư vấn vải cotton")
        );
    }

    @Test
    void testHttpServerError() {
        OllamaService service = createMockService(
                "{\"error\":\"Model unavailable\"}",
                HttpStatus.INTERNAL_SERVER_ERROR
        );

        assertThrows(
                RestClientResponseException.class,
                () -> service.generateAnswer("Tư vấn vải cotton")
        );
    }
}
