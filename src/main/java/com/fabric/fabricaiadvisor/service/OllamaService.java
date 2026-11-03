
package com.fabric.fabricaiadvisor.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Service
public class OllamaService {

    private static final String OLLAMA_URL =
            "http://localhost:11434";

    private static final String MODEL_NAME =
            "gemma3:4b";

    private final RestClient restClient;

    // Constructor dùng khi Spring Boot chạy ứng dụng.
    @Autowired
    public OllamaService() {
        this(createDefaultRestClient());
    }

    // Constructor dùng để truyền HTTP client giả lập khi kiểm thử.
    public OllamaService(RestClient restClient) {
        if (restClient == null) {
            throw new IllegalArgumentException(
                    "RestClient không được để trống."
            );
        }

        this.restClient = restClient;
    }

    private static RestClient createDefaultRestClient() {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(
                Duration.ofSeconds(120)
        );

        return RestClient.builder()
                .baseUrl(OLLAMA_URL)
                .requestFactory(requestFactory)
                .build();
    }

    public String generateAnswer(String question) {

        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException(
                    "Câu hỏi không được để trống."
            );
        }

        Map<String, Object> requestBody = Map.of(
                "model", MODEL_NAME,
                "prompt", question,
                "stream", false
        );

        Map<?, ?> response = restClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new IllegalStateException(
                    "Ollama không trả về dữ liệu."
            );
        }

        Object answer = response.get("response");

        if (!(answer instanceof String text)
                || text.isBlank()) {
            throw new IllegalStateException(
                    "Ollama không trả về nội dung hợp lệ."
            );
        }

        return text.trim();
    }
}
