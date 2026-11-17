package com.fabric.fabricaiadvisor.service;

import com.fabric.fabricaiadvisor.dto.FabricCriteria;
import com.fabric.fabricaiadvisor.repository.FabricRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.*;

@Service
public class FabricAdviceService {

    private final FabricIntentService intentService;
    private final FabricRepository repository;
    private final FabricRecommendationService recommendationService;
    private final FabricExplanationService explanationService;
    private final FabricValidationService validationService;
    private final FabricEvidenceService evidenceService;

    public FabricAdviceService(
            FabricIntentService intentService,
            FabricRepository repository,
            FabricRecommendationService recommendationService,
            FabricExplanationService explanationService,
            FabricValidationService validationService,
            FabricEvidenceService evidenceService) {

        this.intentService = intentService;
        this.repository = repository;
        this.recommendationService = recommendationService;
        this.explanationService = explanationService;
        this.validationService = validationService;
        this.evidenceService = evidenceService;
    }

    private static final List<String> FABRIC_FIELDS = List.of(
            "fabric_id",
            "fabric_name",
            "currency",
            "fiber_composition",
            "construction",
            "gsm_range",
            "typical_width",
            "price_min_vnd_per_m",
            "price_max_vnd_per_m",
            "recommended_garments",
            "properties_and_cautions",
            "verification_status",
            "source_url"
    );
    public Map<String, Object> advise(String query) {
        return advise(query, Map.of("language", "vi"));
    }

    public Map<String, Object> advise(
            String query,
            Map<String, Object> userPreferences) {

        AdviceTimings timings = new AdviceTimings();

        if (query == null || query.isBlank() || query.length() > 1000) {
            throw new IllegalArgumentException(
                    "Câu hỏi phải có từ 1 đến 1000 ký tự."
            );
        }
        Map<String, Object> preferences =
                userPreferences == null ? Map.of() : userPreferences;

        if (preferences.keySet().stream()
                .anyMatch(key -> !Set.of("language", "context").contains(key))) {
            throw new IllegalArgumentException(
                    "Chỉ hỗ trợ userPreferences.language và userPreferences.context."
            );
        }

        Object language = preferences.getOrDefault("language", "vi");

        if (!"vi".equals(language)) {
            throw new IllegalArgumentException(
                    "Phiên bản này hỗ trợ language=vi."
            );
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("language", "vi");
        if (preferences.containsKey("context")) {
            Object context = preferences.get("context");
            if (!(context instanceof String text) || text.isBlank() || text.length() > 200)
                throw new IllegalArgumentException("context phải là chuỗi từ 1 đến 200 ký tự.");
            normalized.put("context", text.trim());
        }
        Map<String, Object> result = adviseInternal(query, normalized, timings);
        result.put("userPreferences", normalized);
        result.put("timings", timings.snapshot());

        return result;
    }

    private Map<String, Object> adviseInternal(
            String query,
            Map<String, Object> preferences,
            AdviceTimings timings) {
        FabricCriteria criteria;

        try (var stage = timings.start("intent")) {
            criteria = preferences.containsKey("context")
                    ? intentService.extract(query, preferences) : intentService.extract(query);

        } catch (RestClientException | IllegalStateException e) {
            return response(
                    "needs_clarification",
                    null,
                    "Chưa thể phân tích câu hỏi tự động lúc này. "
                            + "Vui lòng chọn loại trang phục và nhập ngân sách "
                            + "trong form lọc để nhận kết quả bằng quy tắc.",
                    List.of(),
                    validationService.notRun(
                            "Chưa có đủ điều kiện hợp lệ để tự chuyển sang quy tắc."
                    ),
                    0
            );
        }

        if (criteria.needsClarification()) {
            String question = criteria.clarificationQuestion();

            return response(
                    "needs_clarification",
                    criteria,
                    question == null || question.isBlank()
                            ? "Bạn có thể nói rõ hơn nhu cầu chọn vải không?"
                            : question,
                    List.of(),
                    validationService.notRun(""),
                    0
            );
        }

        List<Map<String, Object>> candidates;
        try (var stage = timings.start("database")) {
            candidates = repository.findCandidates(criteria);
        }

        List<Map<String, Object>> ranked;
        try (var stage = timings.start("ranking")) {
            ranked = recommendationService.rank(candidates, criteria.allProperties());
        }

        FabricEvidenceService.RequiredAssessment required;
        try (var stage = timings.start("evidence")) {
            required = evidenceService.assessRequired(ranked, criteria.requiredProperties());
        }
        if (required.eligible().isEmpty() && !required.uncertain().isEmpty()) {
            return insufficientData(criteria, required.uncertain().stream().limit(5).toList(),
                    ranked.size(), required.missing());
        }
        ranked = required.eligible();
        List<Map<String, Object>> topResults = ranked.stream().limit(5).toList();

        if (topResults.isEmpty()) {
            return response(
                    "no_results",
                    criteria,
                    "Không tìm thấy vải đáp ứng bộ lọc và các điều kiện bắt buộc trong dữ liệu hiện có.",
                    List.of(),
                    validationService.notRun(""),
                    0
            );
        }
        List<String> missing;
        try (var stage = timings.start("evidence")) {
            missing = evidenceService.unknownForAll(ranked);
        }

        if (!missing.isEmpty()) {
            return insufficientData(
                    criteria,
                    topResults,
                    ranked.size(),
                    missing
            );
        }
        FabricExplanationService.Draft previous = null;

        FabricValidationService.Result validation =
                validationService.notRun("");

        List<String> issues = List.of();
        int attempts = 0;

        // Tạo lần đầu và sửa tối đa một lần.
        for (int attempt = 0; attempt < 2; attempt++) {
            attempts++;

            try {
                FabricExplanationService.Draft draft;
                try (var stage = timings.start("explanation")) {
                    if (preferences.containsKey("context")) {
                        draft = attempt == 0
                                ? explanationService.generate(query, criteria, topResults, preferences)
                                : explanationService.correct(query, criteria, topResults, previous, issues, preferences);
                    } else {
                        draft = attempt == 0
                                ? explanationService.generate(query, criteria, topResults)
                                : explanationService.correct(query, criteria, topResults, previous, issues);
                    }
                }

                draft = FabricCatalogSummary.apply(topResults, draft);

                try (var stage = timings.start("validation")) {
                    validation = validationService.validate(query, criteria, topResults, draft);
                }

                if (validation.validated()) {
                    return response(
                            "success",
                            criteria,
                            draft.explanation(),
                            buildRecommendations(topResults, draft),
                            validation,
                            attempts
                    );
                }

                if (validation.rulesPassed()
                        && "unavailable".equals(
                        validation.evaluationStatus())) {

                    // Bỏ lời giải thích AI chưa được kiểm chứng.
                    Map<String, String> reasons = new LinkedHashMap<>();

                    for (Map<String, Object> row : topResults) {
                        reasons.put(
                                row.get("fabric_id").toString(),
                                Objects.toString(
                                        row.get("recommendation_reason"),
                                        "Ứng viên từ bộ lọc, "
                                                + "chưa xác nhận đầy đủ yêu cầu."
                                )
                        );
                    }

                    var fallback = new FabricExplanationService.Draft(
                            "Model kiểm chứng chưa khả dụng. Đây là các ứng viên "
                                    + "lọc theo điều kiện đã nhận diện; các ràng buộc "
                                    + "khác trong câu hỏi chưa được xác nhận.",
                            reasons
                    );

                    return response(
                            "fallback",
                            criteria,
                            fallback.explanation(),
                            buildRecommendations(topResults, fallback),
                            validation,
                            attempts
                    );
                }

                if ("invalid_response".equals(
                        validation.evaluationStatus())) {
                    break;
                }

                previous = draft;
                issues = validation.issues();

            } catch (RestClientException e) {
                // Bản trước đã bị từ chối về nội dung:
                // không đưa các đề xuất đó trở lại qua fallback.
                if ("failed".equals(validation.evaluationStatus())) {
                    break;
                }

                Map<String, String> reasons = new LinkedHashMap<>();

                for (Map<String, Object> row : topResults) {
                    reasons.put(
                            row.get("fabric_id").toString(),
                            Objects.toString(
                                    row.get("recommendation_reason"),
                                    "Ứng viên tham khảo theo bộ lọc đã nhận diện."
                            )
                    );
                }

                var fallback = new FabricExplanationService.Draft(
                        "AI chưa khả dụng. Kết quả dưới đây được lọc và xếp hạng "
                                + "bằng quy tắc theo điều kiện đã nhận diện; "
                                + "chưa xác nhận mọi yêu cầu trong câu hỏi.",
                        reasons
                );

                try (var stage = timings.start("validation")) {
                    validation = validationService.rulesOnly(
                            criteria, topResults, fallback,
                            "Model chính không khả dụng; chưa chạy model kiểm chứng."
                    );
                }
                if (validation.rulesPassed()) {
                    return response(
                            "fallback",
                            criteria,
                            fallback.explanation(),
                            buildRecommendations(topResults, fallback),
                            validation,
                            attempts
                    );
                }

                break;

            } catch (IllegalStateException e) {
                validation = validationService.notRun(
                        "Không tạo được dự thảo AI đúng cấu trúc."
                );

                issues = validation.issues();
            }
        }

        return response(
                "validation_failed",
                criteria,
                "Chưa thể xác nhận câu trả lời đáp ứng đầy đủ yêu cầu. "
                        + "Vui lòng làm rõ yêu cầu hoặc thử lại.",
                List.of(),
                validation,
                attempts
        );
    }

    private Map<String, Object> insufficientData(
            FabricCriteria criteria,
            List<Map<String, Object>> topResults,
            int totalCandidates,
            List<String> missing) {

        String attributes = String.join(", ", missing);

        String reason =
                "Chưa có đủ bằng chứng trong dữ liệu hiện tại để xác nhận: "
                        + attributes + ".";

        Map<String, String> reasons = new LinkedHashMap<>();

        for (Map<String, Object> row : topResults) {
            reasons.put(
                    row.get("fabric_id").toString(),
                    reason
            );
        }

        var candidateDraft = new FabricExplanationService.Draft(
                reason,
                reasons
        );

        Map<String, Object> result = response(
                "insufficient_data",
                criteria,
                "Tìm thấy " + totalCandidates
                        + " ứng viên theo trang phục và ngân sách, "
                        + "nhưng chưa đủ bằng chứng xác nhận: "
                        + attributes
                        + ". Các ứng viên dưới đây chưa được xác nhận "
                        + "đáp ứng đủ đặc tính yêu cầu.",
                List.of(),
                validationService.notRun(""),
                0
        );

        result.put("missingPreferences", missing);

        result.put(
                "candidates",
                buildRecommendations(topResults, candidateDraft)
        );

        return result;
    }

    private List<Map<String, Object>> buildRecommendations(
            List<Map<String, Object>> topResults,
            FabricExplanationService.Draft draft) {

        List<Map<String, Object>> recommendations = new ArrayList<>();

        for (int i = 0; i < topResults.size(); i++) {
            Map<String, Object> source = topResults.get(i);

            String id = source.get("fabric_id").toString();
            String reason = draft.reasonsById().get(id);

            if (!draft.reasonsById().containsKey(id)) continue;
            if (reason == null || reason.isBlank())
                throw new IllegalStateException("Thiếu lời giải thích cho vải đã chọn.");

            Map<String, Object> fabric = new LinkedHashMap<>();

            for (String field : FABRIC_FIELDS) {
                fabric.put(field, source.get(field));
            }

            fabric.put("price_unit", "VND/m");
            fabric.put("id", source.get("fabric_id"));
            fabric.put("name", source.get("fabric_name"));
            fabric.put("type", source.get("construction"));
            Object min = source.get("price_min_vnd_per_m");
            Object max = source.get("price_max_vnd_per_m");
            fabric.put("price", min != null && max != null
                    && new java.math.BigDecimal(min.toString()).compareTo(new java.math.BigDecimal(max.toString())) == 0
                    ? min : null);
            String description = Objects.toString(source.get("properties_and_cautions"), "");
            fabric.put("characteristics", description.isBlank() ? List.of() : List.of(description));
            fabric.put("source", source.get("source_url"));

            Map<String, Object> item = new LinkedHashMap<>();

            item.put("rank", recommendations.size() + 1);
            item.put("fabric", fabric);
            item.put("reason", reason);
            item.put("confidence", null);
            item.put("confidenceStatus", "not_calibrated");
            item.put("rankingScore", source.get("ranking_score"));
            item.put("matchedPreferences", source.get("matched_preferences"));
            item.put("cautionPreferences", source.get("caution_preferences"));
            item.put("unknownPreferences", source.get("unknown_preferences"));

            recommendations.add(item);
        }

        return recommendations;
    }

    private Map<String, Object> response(
            String status,
            FabricCriteria criteria,
            String explanation,
            List<Map<String, Object>> recommendations,
            FabricValidationService.Result validation,
            int attempts) {

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("status", status);
        result.put("criteria", criteria);
        result.put("recommendations", recommendations);
        result.put("candidates", List.of());
        result.put("missingPreferences", List.of());
        result.put("explanation", explanation);
        result.put("factCheck", validation.toMap());
        result.put("explanationAttempts", attempts);

        result.put(
                "notice",
                "Kiểm chứng đánh giá tính nhất quán với database; "
                        + "không xác minh nguồn dữ liệu ngoài thực tế. "
                        + "Giữ nguyên verification_status của từng bản ghi."
        );

        return result;
    }
}