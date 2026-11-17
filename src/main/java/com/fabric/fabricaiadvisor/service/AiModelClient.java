package com.fabric.fabricaiadvisor.service;

public interface AiModelClient {

    String generateJson(String systemPrompt, String input);

    String getModelName();

    default void checkAvailability() {
        throw new IllegalStateException(
                "Adapter chưa hỗ trợ kiểm tra model."
        );
    }
}