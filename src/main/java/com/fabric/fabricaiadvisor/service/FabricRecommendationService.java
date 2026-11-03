
package com.fabric.fabricaiadvisor.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class FabricRecommendationService {

    private final FabricService fabricService;

    public FabricRecommendationService(
            FabricService fabricService) {
        this.fabricService = fabricService;
    }

    // Cum tu ho tro tung nhom so thich.
    private static final Map<String, List<String>> POSITIVE =
            Map.of(
                    "thoang mat",
                    List.of(
                            "thoang",
                            "thoang khi",
                            "thoang mat",
                            "thong thoang",
                            "mat me"
                    ),

                    "it nhan",
                    List.of(
                            "it nhan",
                            "chong nhan",
                            "han che nhan"
                    ),

                    "mem",
                    List.of(
                            "mem",
                            "mem mai",
                            "em ai"
                    ),

                    "co gian",
                    List.of(
                            "co gian",
                            "dan hoi"
                    ),

                    "tham hut",
                    List.of(
                            "tham hut",
                            "hut am"
                    )
            );

    // Cum tu cho thay co the khong dap ung so thich.
    private static final Map<String, List<String>> NEGATIVE =
            Map.of(
                    "thoang mat",
                    List.of(
                            "khong thoang",
                            "kem thoang",
                            "it thoang",
                            "bi nong",
                            "bi ham",
                            "ham bi"
                    ),

                    "it nhan",
                    List.of(
                            "de nhan",
                            "hay nhan",
                            "nhan nhieu",
                            "rat nhan",
                            "khong chong nhan",
                            "khong it nhan"
                    ),

                    "mem",
                    List.of(
                            "khong mem",
                            "kem mem",
                            "tho rap",
                            "cung"
                    ),

                    "co gian",
                    List.of(
                            "khong co gian",
                            "it co gian",
                            "kem co gian",
                            "khong dan hoi"
                    ),

                    "tham hut",
                    List.of(
                            "khong tham hut",
                            "kem tham hut",
                            "it tham hut",
                            "khong hut am"
                    )
            );

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        String text = Normalizer.normalize(
                value.toLowerCase(Locale.ROOT),
                Normalizer.Form.NFD
        );

        text = text.replaceAll("\\p{M}+", "")
                .replace('đ', 'd');

        return text.replaceAll("\\s+", " ").trim();
    }

    // Tim cum tu theo ranh gioi tu, tranh khop
    // nham mot phan cua tu dai hon.
    private boolean containsPhrase(
            String text,
            List<String> phrases) {

        for (String phrase : phrases) {
            String expression =
                    "(?<![a-z0-9])" +
                            Pattern.quote(phrase) +
                            "(?![a-z0-9])";

            if (Pattern.compile(expression)
                    .matcher(text)
                    .find()) {
                return true;
            }
        }

        return false;
    }

    private String identifyPreference(String value) {
        String text = normalize(value);

        if (containsPhrase(text, List.of(
                "thoang", "thoang mat",
                "thoang khi", "thong thoang",
                "mat me"))) {
            return "thoang mat";
        }

        if (containsPhrase(text, List.of(
                "it nhan", "chong nhan",
                "han che nhan"))) {
            return "it nhan";
        }

        if (containsPhrase(text, List.of(
                "mem", "mem mai", "em ai"))) {
            return "mem";
        }

        if (containsPhrase(text, List.of(
                "co gian", "dan hoi"))) {
            return "co gian";
        }

        if (containsPhrase(text, List.of(
                "tham hut", "hut am"))) {
            return "tham hut";
        }

        return null;
    }



    private String buildRecommendationReason(
            List<String> matched,
            List<String> cautions,
            List<String> unknown,
            int rankingScore) {

        StringBuilder reason = new StringBuilder();

        if (!matched.isEmpty()) {
            reason.append(
                    "Theo mô tả dữ liệu, có bằng chứng hỗ trợ sở thích: "
            );
            reason.append(String.join(", ", matched));
            reason.append(" (+");
            reason.append(matched.size());
            reason.append(" điểm). ");
        }

        if (!cautions.isEmpty()) {
            reason.append(
                    "Có cảnh báo đối với sở thích: "
            );
            reason.append(String.join(", ", cautions));
            reason.append(" (-");
            reason.append(cautions.size());
            reason.append(" điểm). ");
        }

        if (!unknown.isEmpty()) {
            reason.append(
                    "Chưa đủ thông tin để đánh giá sở thích: "
            );
            reason.append(String.join(", ", unknown));
            reason.append(". ");
        }

        if (matched.isEmpty()
                && cautions.isEmpty()
                && unknown.isEmpty()) {
            reason.append(
                    "Người dùng chưa nhập sở thích để xếp hạng. "
            );
        }

        reason.append("Tổng điểm xếp hạng: ");
        reason.append(matched.size());
        reason.append(" - ");
        reason.append(cautions.size());
        reason.append(" = ");
        reason.append(rankingScore);
        reason.append(". ");

        reason.append(
                "Kết quả chỉ mang tính tham khảo, " +
                        "dữ liệu chưa được xác minh độc lập."
        );

        return reason.toString();
    }



    public List<Map<String, Object>> recommend(
            String garment,
            double budget,
            String preference) {

        List<Map<String, Object>> fabrics =
                fabricService.getFabricsByGarmentAndBudget(
                        garment, budget
                );

        List<Map<String, Object>> results =
                new ArrayList<>();

        String preferenceText =
                preference == null ? "" : preference;

        String[] keywords = preferenceText.split("[,;]+");

        Set<String> requestedPreferences =
                new LinkedHashSet<>();

        for (String keyword : keywords) {
            String clean = keyword.trim();

            if (!clean.isEmpty()) {
                requestedPreferences.add(clean);
            }
        }

        for (Map<String, Object> fabric : fabrics) {

            Object rawDescription =
                    fabric.get("properties_and_cautions");


            String description = normalize(
                    rawDescription == null
                            ? ""
                            : rawDescription.toString()
            );

            description = description.replaceAll(
                    "(?<![a-z0-9])khong\\s+de\\s+nhan(?![a-z0-9])",
                    "it nhan"
            );

            int score = 0;
            int rankingScore = 0;
            List<String> matched = new ArrayList<>();
            List<String> cautions = new ArrayList<>();
            List<String> unknown = new ArrayList<>();

            for (String preferenceItem :
                    requestedPreferences) {

                String category =
                        identifyPreference(preferenceItem);

                if (category == null) {
                    unknown.add(preferenceItem);
                    continue;
                }

                boolean negative = containsPhrase(
                        description,
                        NEGATIVE.getOrDefault(
                                category, List.of()
                        )
                );

                boolean positive = containsPhrase(
                        description,
                        POSITIVE.getOrDefault(
                                category, List.of()
                        )
                );

                // Neu co ca dau hieu tich cuc va
                // bat loi, uu tien canh bao.
                if (negative) {
                    cautions.add(preferenceItem);
                    rankingScore--;

                } else if (positive) {
                    matched.add(preferenceItem);
                    score++;
                    rankingScore++;

                } else {
                    unknown.add(preferenceItem);
                }
            }

            Map<String, Object> recommendation =
                    new LinkedHashMap<>(fabric);

            recommendation.put("match_score", score);
            recommendation.put("ranking_score", rankingScore);
            recommendation.put(
                    "recommendation_reason",
                    buildRecommendationReason(
                            matched,
                            cautions,
                            unknown,
                            rankingScore
                    )
            );
            recommendation.put(
                    "ranking_explanation",
                    "Diem xep hang = so so thich duoc ho tro " +
                            "- so so thich co canh bao"
            );
            recommendation.put(
                    "matched_preferences", matched
            );

            recommendation.put(
                    "caution_preferences", cautions
            );

            recommendation.put(
                    "unknown_preferences", unknown
            );

            recommendation.put(
                    "scoring_method",
                    "Xep hang so bo bang quy tac tu khoa"
            );

            recommendation.put(
                    "recommendation_note",
                    "Du lieu tham khao, " +
                            "chua xac minh doc lap"
            );

            results.add(recommendation);
        }

        results.sort(
                Comparator
                        .comparingInt(
                                (Map<String, Object> item) ->
                                        (Integer) item.get("ranking_score")
                        )
                        .thenComparingInt(
                                item ->
                                        (Integer) item.get("match_score")
                        )
                        .reversed()
        );

        return results;
    }
}
