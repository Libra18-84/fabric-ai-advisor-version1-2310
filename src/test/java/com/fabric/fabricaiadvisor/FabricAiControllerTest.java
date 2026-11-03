
package com.fabric.fabricaiadvisor;

import com.fabric.fabricaiadvisor.controller.FabricAiController;
import com.fabric.fabricaiadvisor.service.OllamaService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FabricAiControllerTest {

    private OllamaService ollamaService;
    private FabricAiController controller;

    @BeforeEach
    void setUp() {
        ollamaService = mock(OllamaService.class);
        controller = new FabricAiController(ollamaService);
    }

    @Test
    void testRejectNullRequest() {
        ResponseEntity<Map<String, Object>> response =
                controller.ask(null);

        assertEquals(400, response.getStatusCode().value());
        verifyNoInteractions(ollamaService);
    }

    @Test
    void testRejectMissingQuestion() {
        ResponseEntity<Map<String, Object>> response =
                controller.ask(Map.of("other", "value"));

        assertEquals(400, response.getStatusCode().value());
        verifyNoInteractions(ollamaService);
    }

    @Test
    void testRejectEmptyQuestion() {
        ResponseEntity<Map<String, Object>> response =
                controller.ask(Map.of("question", ""));

        assertEquals(400, response.getStatusCode().value());
        verifyNoInteractions(ollamaService);
    }

    @Test
    void testRejectNonStringQuestion() {
        ResponseEntity<Map<String, Object>> response =
                controller.ask(Map.of("question", 123));

        assertEquals(400, response.getStatusCode().value());
        verifyNoInteractions(ollamaService);
    }

    @Test
    void testRejectTooLongQuestion() {
        ResponseEntity<Map<String, Object>> response =
                controller.ask(Map.of(
                        "question",
                        "a".repeat(1001)
                ));

        assertEquals(400, response.getStatusCode().value());
        verifyNoInteractions(ollamaService);
    }

    @Test
    void testSuccessfulAnswer() {
        String question = "Vải cotton có phù hợp may sơ mi không?";

        when(ollamaService.generateAnswer(question))
                .thenReturn("Cotton có thể phù hợp để may áo sơ mi.");

        ResponseEntity<Map<String, Object>> response =
                controller.ask(Map.of("question", question));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());

        assertEquals(
                "Cotton có thể phù hợp để may áo sơ mi.",
                response.getBody().get("answer")
        );

        assertEquals(
                "gemma3:4b",
                response.getBody().get("model")
        );

        assertEquals(
                true,
                response.getBody().get("ai_connected")
        );

        verify(ollamaService).generateAnswer(question);
    }

    @Test
    void testOllamaConnectionFailure() {
        when(ollamaService.generateAnswer(anyString()))
                .thenThrow(new ResourceAccessException(
                        "Ollama connection refused"
                ));

        ResponseEntity<Map<String, Object>> response =
                controller.ask(Map.of(
                        "question",
                        "Tư vấn vải cotton"
                ));

        assertEquals(503, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(
                false,
                response.getBody().get("ai_connected")
        );
    }

    @Test
    void testInvalidOllamaResponse() {
        when(ollamaService.generateAnswer(anyString()))
                .thenThrow(new IllegalStateException(
                        "Invalid response"
                ));

        ResponseEntity<Map<String, Object>> response =
                controller.ask(Map.of(
                        "question",
                        "Tư vấn vải linen"
                ));

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(
                false,
                response.getBody().get("ai_connected")
        );
    }
}

