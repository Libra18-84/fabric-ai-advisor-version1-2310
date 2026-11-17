package com.fabric.fabricaiadvisor.controller;

import com.fabric.fabricaiadvisor.service.FabricAdviceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;

import java.util.*;

@RestController
@RequestMapping("/api")
public class FabricAdviceController {

    private final FabricAdviceService adviceService;

    public FabricAdviceController(FabricAdviceService adviceService) {
        this.adviceService = adviceService;
    }

    @PostMapping("/advise")
    public ResponseEntity<Map<String, Object>> advise(
            @RequestBody(required = false) Map<String, Object> request) {

        return handle(adviceService, request, "query");
    }

    // Dùng chung cho API mới và API /api/ai/ask.
    static ResponseEntity<Map<String, Object>> handle(
            FabricAdviceService service,
            Map<String, Object> request,
            String queryField) {

        if (request == null
                || !(request.get(queryField) instanceof String query)
                || query.isBlank()
                || query.length() > 1000) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            "Câu hỏi phải có từ 1 đến 1000 ký tự."
                    )
            );
        }

        Map<String, Object> preferences = new LinkedHashMap<>();

        if (request.containsKey("userPreferences")
                && request.get("userPreferences") != null) {

            if (!(request.get("userPreferences")
                    instanceof Map<?, ?> raw)) {

                return ResponseEntity.badRequest().body(
                        Map.of(
                                "error",
                                "userPreferences phải là một JSON object."
                        )
                );
            }

            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    return ResponseEntity.badRequest().body(
                            Map.of("error", "Tên tùy chọn không hợp lệ.")
                    );
                }

                preferences.put(key, entry.getValue());
            }
        }

        try {
            return ResponseEntity.ok(
                    service.advise(query, preferences)
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", e.getMessage())
            );

        } catch (RestClientException e) {
            return ResponseEntity.status(503).body(
                    Map.of("error", "Không nhận được phản hồi từ AI.")
            );

        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(
                    Map.of("error", "Dữ liệu AI không hợp lệ. Hãy thử lại.")
            );
        }
    }
}