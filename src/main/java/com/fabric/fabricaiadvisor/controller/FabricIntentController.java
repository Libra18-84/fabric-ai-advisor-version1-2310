package com.fabric.fabricaiadvisor.controller;

import com.fabric.fabricaiadvisor.service.FabricIntentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@RestController
@RequestMapping("/api/intent")
public class FabricIntentController {

    private final FabricIntentService intentService;

    public FabricIntentController(FabricIntentService intentService) {
        this.intentService = intentService;
    }

    @PostMapping("/extract")
    public ResponseEntity<?> extract(
            @RequestBody(required = false) Map<String, Object> request) {

        if (request == null || !(request.get("query") instanceof String query)
                || query.isBlank()
                || query.length() > 1000) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Câu hỏi phải có từ 1 đến 1000 ký tự.")
            );
        }

        try {
            return ResponseEntity.ok(intentService.extract(query));
        } catch (RestClientException e) {
            return ResponseEntity.status(503).body(
                    Map.of("error", "Không nhận được phản hồi từ model AI.")
            );
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(
                    Map.of("error", e.getMessage())
            );
        }
    }
}