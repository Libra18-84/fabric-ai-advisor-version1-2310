package com.fabric.fabricaiadvisor.service;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** llama-server OpenAI-compatible HTTP adapter; plain class, not a Spring bean. */
public class LlamaCppModelClient implements AiModelClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final RestClient client;
    private final RestClient healthClient;
    private final String model;

    public LlamaCppModelClient(String endpoint, String model, Duration readTimeout) {
        this(createClient(endpoint, readTimeout), createClient(endpoint, Duration.ofSeconds(5)), model);
    }

    public LlamaCppModelClient(RestClient client, String model) {
        this(client, client, model);
    }

    private LlamaCppModelClient(RestClient client, RestClient healthClient, String model) {
        if (client == null || healthClient == null || model == null || model.isBlank())
            throw new IllegalArgumentException("HTTP client hoặc alias model llama.cpp không hợp lệ.");
        this.client = client;
        this.healthClient = healthClient;
        this.model = model.trim();
    }

    private static RestClient createClient(String endpoint, Duration timeout) {
        if (endpoint == null || endpoint.isBlank() || timeout == null || timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("Endpoint hoặc thời gian chờ llama.cpp không hợp lệ.");
        var factory = new JdkClientHttpRequestFactory(HTTP);
        factory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(endpoint).requestFactory(factory).build();
    }

    @Override
    public String getModelName() { return model; }

    @Override
    public void checkAvailability() {
        Map<?, ?> health = healthClient.get().uri("/health").retrieve().body(Map.class);
        if (health == null || !"ok".equals(health.get("status")))
            throw new IllegalStateException("llama-server chưa sẵn sàng.");
        Map<?, ?> response = healthClient.get().uri("/v1/models").retrieve().body(Map.class);
        if (response != null && response.get("data") instanceof List<?> models) {
            for (Object item : models) {
                if (item instanceof Map<?, ?> row && model.equals(row.get("id"))) return;
            }
        }
        throw new IllegalStateException("llama-server chưa cung cấp alias model: " + model);
    }

    @Override
    public String generateJson(String systemPrompt, String input) {
        if (systemPrompt == null || systemPrompt.isBlank() || input == null || input.isBlank())
            throw new IllegalArgumentException("Nội dung gửi AI không được để trống.");
        Map<?, ?> response = client.post().uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", model,
                        "messages", List.of(Map.of("role", "system", "content", systemPrompt),
                                            Map.of("role", "user", "content", input)),
                        "temperature", 0,
                        "max_tokens", 2048,
                        "stream", false,
                        "response_format", Map.of("type", "json_object")))
                .retrieve().body(Map.class);
        if (response == null || !(response.get("choices") instanceof List<?> choices)
                || choices.size() != 1 || !(choices.getFirst() instanceof Map<?, ?> choice))
            throw invalidResponse();
        Object reason = choice.get("finish_reason");
        if (!(reason instanceof String finish) || !Set.of("stop").contains(finish))
            throw new IllegalStateException("llama.cpp chưa hoàn tất câu trả lời JSON (finish_reason=" + reason + ").");
        if (!(choice.get("message") instanceof Map<?, ?> message)
                || !(message.get("content") instanceof String text) || text.isBlank()
                || (message.get("refusal") instanceof String refusal && !refusal.isBlank()))
            throw invalidResponse();
        return text.trim();
    }

    private IllegalStateException invalidResponse() {
        return new IllegalStateException("llama.cpp trả phản hồi thiếu choices/message/content hợp lệ.");
    }
}
