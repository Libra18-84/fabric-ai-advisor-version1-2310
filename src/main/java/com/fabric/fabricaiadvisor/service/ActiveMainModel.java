package com.fabric.fabricaiadvisor.service;

import org.springframework.stereotype.Service;

@Service
public class ActiveMainModel implements AiModelClient {

    private final AiModelRegistry registry;

    public ActiveMainModel(AiModelRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String generateJson(String systemPrompt, String input) {
        return registry.snapshot()
                .main()
                .generateJson(systemPrompt, input);
    }

    @Override
    public String getModelName() {
        return registry.snapshot().main().getModelName();
    }
}