package com.fabric.fabricaiadvisor.service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class FabricRecommendationService {
    private final FabricService fabricService;

    public FabricRecommendationService(FabricService fabricService) {
        this.fabricService = fabricService;
    }

    public boolean supportsPreference(String preference) {
        return FabricEvidenceRules.category(preference) != null;
    }

    public List<Map<String, Object>> recommend(String garment, double budget, String preference) {
        var candidates = fabricService.getFabricsByGarmentAndBudget(garment, budget);
        return rank(candidates, List.of((preference == null ? "" : preference).split("[,;]+")));
    }

    public List<Map<String, Object>> rank(List<Map<String, Object>> candidates, List<String> preferences) {
        if (candidates == null) throw new IllegalArgumentException("Danh sách ứng viên không hợp lệ.");
        Map<String, String> unique = new LinkedHashMap<>();
        if (preferences != null) {
            for (String preference : preferences) {
                String canonical = FabricEvidenceRules.canonical(preference);
                if (!canonical.isBlank()) unique.putIfAbsent(canonical, preference.trim());
            }
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> fabric : candidates) {
            List<String> matched = new ArrayList<>(), cautions = new ArrayList<>(), unknown = new ArrayList<>();
            for (String preference : unique.values()) {
                switch (FabricEvidenceRules.evaluate(fabric.get("properties_and_cautions"), preference)) {
                    case MATCH -> matched.add(preference);
                    case CONFLICT -> cautions.add(preference);
                    case UNKNOWN -> unknown.add(preference);
                }
            }
            int score = matched.size() - cautions.size();
            Map<String, Object> item = new LinkedHashMap<>(fabric);
            item.put("match_score", matched.size());
            item.put("ranking_score", score);
            item.put("matched_preferences", matched);
            item.put("caution_preferences", cautions);
            item.put("unknown_preferences", unknown);
            item.put("recommendation_reason", reason(matched, cautions, unknown, score));
            item.put("ranking_explanation", "Điểm = số đặc tính được hỗ trợ - số đặc tính có cảnh báo");
            item.put("scoring_method", "Xếp hạng sơ bộ bằng quy tắc bằng chứng trong mô tả");
            item.put("recommendation_note", "Dữ liệu tham khảo, chưa xác minh độc lập");
            results.add(item);
        }
        results.sort(Comparator.comparingInt((Map<String, Object> row) -> (Integer) row.get("ranking_score"))
                .thenComparingInt(row -> (Integer) row.get("match_score")).reversed());
        return results;
    }

    private String reason(List<String> matched, List<String> cautions, List<String> unknown, int score) {
        StringBuilder result = new StringBuilder();
        if (!matched.isEmpty()) result.append("Theo mô tả dữ liệu, có bằng chứng hỗ trợ: ")
                .append(String.join(", ", matched)).append(". ");
        if (!cautions.isEmpty()) result.append("Có cảnh báo đối với: ")
                .append(String.join(", ", cautions)).append(". ");
        if (!unknown.isEmpty()) result.append("Chưa đủ thông tin để đánh giá: ")
                .append(String.join(", ", unknown)).append(". ");
        if (matched.isEmpty() && cautions.isEmpty() && unknown.isEmpty())
            result.append("Người dùng chưa nhập sở thích để xếp hạng. ");
        return result.append("Tổng điểm xếp hạng: ").append(matched.size()).append(" - ")
                .append(cautions.size()).append(" = ").append(score)
                .append(". Kết quả chỉ mang tính tham khảo, dữ liệu chưa được xác minh độc lập.").toString();
    }
}
