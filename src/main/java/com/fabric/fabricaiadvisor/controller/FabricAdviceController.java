
package com.fabric.fabricaiadvisor.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FabricAdviceController {

    @PostMapping("/advise")
    public ResponseEntity<Map<String, Object>> advise(
            @RequestBody Map<String, Object> request) {

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("status", "received");
        response.put("message",
                "Spring Boot da nhan yeu cau thanh cong.");
        response.put("received_data", request);
        response.put("ai_connected", false);

        return ResponseEntity.ok(response);
    }
}
