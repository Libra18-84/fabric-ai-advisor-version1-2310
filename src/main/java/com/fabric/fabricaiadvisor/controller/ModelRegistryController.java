package com.fabric.fabricaiadvisor.controller;

import com.fabric.fabricaiadvisor.service.AiModelRegistry;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/models")
public class ModelRegistryController {

    private final AiModelRegistry registry;

    public ModelRegistryController(AiModelRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        var selected = registry.snapshot();

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("revision", selected.revision());

        result.put(
                "mainModel",
                selected.main().getModelName()
        );

        result.put(
                "evaluationModel",
                selected.evaluation() == null
                        ? null
                        : selected.evaluation().getModelName()
        );

        result.put(
                "registeredModels",
                selected.clients().keySet()
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(result);
    }
}