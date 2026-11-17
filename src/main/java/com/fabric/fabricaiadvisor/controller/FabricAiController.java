package com.fabric.fabricaiadvisor.controller;

import com.fabric.fabricaiadvisor.service.FabricAdviceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class FabricAiController {

    private final FabricAdviceService adviceService;

    public FabricAiController(FabricAdviceService adviceService) {
        this.adviceService = adviceService;
    }

    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> ask(
            @RequestBody(required = false) Map<String, Object> request) {

        return FabricAdviceController.handle(
                adviceService, request, "question"
        );
    }
}