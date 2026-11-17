package com.fabric.fabricaiadvisor.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class FabricEvidenceService {

    private final FabricRecommendationService recommendationService;

    public FabricEvidenceService(
            FabricRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    public List<String> unknownForAll(
            List<Map<String, Object>> ranked) {

        if (ranked == null || ranked.isEmpty()) {
            return List.of();
        }

        Set<String> commonUnknown = null;

        for (Map<String, Object> row : ranked) {
            Object raw = row.get("unknown_preferences");

            if (!(raw instanceof List<?> values)) {
                return List.of();
            }

            Set<String> unknown = new LinkedHashSet<>();

            for (Object value : values) {
                if (value instanceof String text
                        && !text.isBlank()) {
                    unknown.add(text);
                }
            }

            if (commonUnknown == null) {
                commonUnknown = unknown;
            } else {
                commonUnknown.retainAll(unknown);
            }
        }

        return commonUnknown.stream()
                .filter(recommendationService::supportsPreference)
                .toList();
    }

    public record RequiredAssessment(
            List<Map<String, Object>> eligible,
            List<Map<String, Object>> uncertain,
            List<String> missing) {}

    public RequiredAssessment assessRequired(
            List<Map<String, Object>> ranked,
            List<String> required) {
        if (required == null || required.isEmpty())
            return new RequiredAssessment(ranked, List.of(), List.of());
        List<Map<String, Object>> eligible = new ArrayList<>(), uncertain = new ArrayList<>();
        Set<String> missing = new LinkedHashSet<>();
        for (var row : ranked) {
            boolean conflict = false;
            List<String> unknown = new ArrayList<>();
            for (String property : required) {
                var state = FabricEvidenceRules.evaluate(row.get("properties_and_cautions"), property);
                if (state == FabricEvidenceRules.State.CONFLICT) conflict = true;
                if (state == FabricEvidenceRules.State.UNKNOWN) unknown.add(property);
            }
            if (conflict) continue;
            if (unknown.isEmpty()) eligible.add(row);
            else {
                uncertain.add(row);
                missing.addAll(unknown);
            }
        }
        return new RequiredAssessment(eligible, uncertain, new ArrayList<>(missing));
    }
}
