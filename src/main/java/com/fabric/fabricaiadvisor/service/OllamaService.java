package com.fabric.fabricaiadvisor.service;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class OllamaService implements AiModelClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final RestClient restClient;
    private final RestClient healthClient;
    private final String modelName;

    public OllamaService(String endpoint, String modelName) {
        this(endpoint, modelName, Duration.ofSeconds(120));
    }

    public OllamaService(
            String endpoint,
            String modelName,
            Duration readTimeout) {

        this(
                createDefaultRestClient(endpoint, readTimeout),
                modelName,
                createDefaultRestClient(
                        endpoint,
                        Duration.ofSeconds(5)
                )
        );
    }

    public OllamaService() {
        this("http://localhost:11434", "gemma3:4b");
    }

    public OllamaService(RestClient restClient) {
        this(restClient, "gemma3:4b");
    }

    public OllamaService(RestClient restClient, String modelName) {
        this(restClient, modelName, restClient);
    }

    private OllamaService(
            RestClient restClient,
            String modelName,
            RestClient healthClient) {

        if (restClient == null
                || modelName == null
                || modelName.isBlank()) {

            throw new IllegalArgumentException(
                    "HTTP client hoặc tên model không hợp lệ."
            );
        }

        this.restClient = restClient;
        this.healthClient = healthClient;
        this.modelName = modelName;
    }

    private static RestClient createDefaultRestClient(
            String endpoint,
            Duration readTimeout) {

        if (readTimeout == null
                || readTimeout.isZero()
                || readTimeout.isNegative()) {

            throw new IllegalArgumentException(
                    "Thời gian chờ phải lớn hơn 0."
            );
        }

        var factory = new JdkClientHttpRequestFactory(HTTP);
        factory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(endpoint)
                .requestFactory(factory)
                .build();
    }

    public String generateAnswer(String question) {
        requireText(question);

        return send(Map.of(
                "model", modelName,
                "prompt", question,
                "stream", false
        ));
    }

    @Override
    public String generateJson(String systemPrompt, String question) {
        requireText(systemPrompt);
        requireText(question);

        return send(Map.of(
                "model", modelName,
                "system", systemPrompt,
                "prompt", question,
                "format", "json",
                "stream", false,
                "options", Map.of("temperature", 0)
        ));
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    public void checkAvailability() {
        Map<?, ?> response = healthClient.get()
                .uri("/api/tags")
                .retrieve()
                .body(Map.class);

        if (response != null
                && response.get("models") instanceof List<?> models) {

            String tagged = modelName.contains(":")
                    ? modelName
                    : modelName + ":latest";

            for (Object item : models) {
                if (item instanceof Map<?, ?> row) {
                    for (String field : List.of("name", "model")) {
                        Object name = row.get(field);

                        if (modelName.equals(name)
                                || tagged.equals(name)) {
                            return;
                        }
                    }
                }
            }
        }

        throw new IllegalStateException(
                "Ollama chưa liệt kê model yêu cầu."
        );
    }

    private String send(Map<String, Object> requestBody) {
        Map<?, ?> response = restClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (response == null
                || !(response.get("response") instanceof String text)
                || text.isBlank()) {

            throw new IllegalStateException(
                    "Ollama không trả về nội dung hợp lệ."
            );
        }

        return text.trim();
    }

    private static void requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Nội dung gửi AI không được để trống."
            );
        }
    }
}