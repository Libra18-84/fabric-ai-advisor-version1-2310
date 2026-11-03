
package com.fabric.fabricaiadvisor.controller;

import com.fabric.fabricaiadvisor.service.OllamaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class FabricAiController {

    private static final int MAX_QUESTION_LENGTH = 1000;

    private final OllamaService ollamaService;

    public FabricAiController(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> ask(
            @RequestBody(required = false) Map<String, Object> request) {

        if (request == null
                || !(request.get("question") instanceof String question)
                || question.isBlank()) {

            return ResponseEntity.badRequest().body(
                    Map.of("error", "Câu hỏi không được để trống.")
            );
        }

        if (question.length() > MAX_QUESTION_LENGTH) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Câu hỏi không được vượt quá 1000 ký tự.")
            );
        }

        try {
            String answer = ollamaService.generateAnswer(question);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("answer", answer);
            response.put("model", "gemma3:4b");
            response.put("ai_connected", true);
            response.put(
                    "notice",
                    "Nội dung do AI tạo ra, chỉ mang tính tham khảo. " +
                            "Thông tin kỹ thuật về vải chưa được xác minh độc lập."
            );

            return ResponseEntity.ok(response);

        } catch (RestClientException e) {
            return ResponseEntity.status(503).body(
                    Map.of(
                            "error",
                            "Không thể kết nối hoặc nhận phản hồi từ Ollama.",
                            "ai_connected",
                            false
                    )
            );

        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(
                    Map.of(
                            "error",
                            "Ollama trả về dữ liệu không hợp lệ.",
                            "ai_connected",
                            false
                    )
            );
        }
    }
}
