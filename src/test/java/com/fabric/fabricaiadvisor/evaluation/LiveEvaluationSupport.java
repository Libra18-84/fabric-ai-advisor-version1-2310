package com.fabric.fabricaiadvisor.evaluation;

import tools.jackson.databind.JsonNode;
import java.util.*;

/** Report calculations and API contract checks; these do not judge prose quality. */
final class LiveEvaluationSupport {
    /** A semantic rejection is a measured result; transport/schema failures stop the run. */
    static boolean usableResponse(int httpStatus, JsonNode body, boolean lastDraftGenerated) {
        if (httpStatus != 200 || !body.path("criteria").isObject()) return false;
        String status = body.path("status").asText();
        if (!status.equals("validation_failed"))
            return Set.of("success", "needs_clarification", "insufficient_data", "no_results").contains(status);
        JsonNode check = body.path("factCheck");
        if (!check.path("rulesPassed").isBoolean() || !check.path("issues").isArray() || check.path("issues").isEmpty()) return false;
        String evaluation = check.path("evaluationStatus").asText();
        if (evaluation.equals("failed") && check.path("rulesPassed").asBoolean()) return true;
        return evaluation.equals("not_run") && !check.path("rulesPassed").asBoolean() && lastDraftGenerated;
    }


    private LiveEvaluationSupport() {}

    static Map<String, Object> latency(List<Double> values) {
        var sorted = values.stream().sorted().toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("samples", sorted.size());
        result.put("unit", "ms");
        result.put("method", "nearest_rank");
        for (int p : List.of(50, 95, 99)) {
            result.put("p" + p, sorted.isEmpty() ? null
                    : sorted.get((int) Math.ceil(p / 100.0 * sorted.size()) - 1));
        }
        result.put("interpretation", sorted.size() < 100
                ? "Small sample; descriptive only, not a stable tail-latency estimate."
                : "Sequential single-client workload; not a concurrency benchmark.");
        return result;
    }

    record Judgement(boolean expectedPass, boolean rulesPassed, String actualStatus) {
        boolean classified() {
            return rulesPassed && Set.of("passed", "failed").contains(actualStatus);
        }
        boolean agrees() { return classified() && expectedPass == "passed".equals(actualStatus); }
    }

    static Map<String, Object> quality(List<Judgement> values) {
        long classified = values.stream().filter(Judgement::classified).count();
        long correct = values.stream().filter(Judgement::agrees).count();
        long tp = values.stream().filter(v -> v.agrees() && v.expectedPass()).count();
        long tn = correct - tp;
        long fp = values.stream().filter(v -> v.classified() && !v.expectedPass()
                && "passed".equals(v.actualStatus())).count();
        long fn = classified - tp - tn - fp;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("labelStatus", "proposed_requires_human_review");
        result.put("cases", values.size());
        result.put("classified", classified);
        result.put("unclassified", values.size() - classified);
        result.put("correct", correct);
        result.put("coverage", ratio(classified, values.size()));
        result.put("agreementOnClassified", ratio(correct, classified));
        result.put("agreementOnAllCases", ratio(correct, values.size()));
        result.put("truePositive", tp); result.put("trueNegative", tn);
        result.put("falsePositive", fp); result.put("falseNegative", fn);
        result.put("interpretation", "Agreement with proposed labels, not production accuracy or proof of >95% accuracy.");
        return result;
    }

    private static Double ratio(long numerator, long denominator) {
        return denominator == 0 ? null : (double) numerator / denominator;
    }

    static List<String> adviceIssues(JsonNode expected, JsonNode actual, Map<String, JsonNode> catalog) {
        List<String> issues = new ArrayList<>();
        String status = expected.path("status").asText();
        if (!status.equals(actual.path("status").asText())) issues.add("Unexpected status");
        JsonNode criteria = actual.path("criteria");
        if (expected.has("garment")) {
            String garment = criteria.path("garment").asText();
            if (!Set.of("sơ mi", "áo sơ mi").contains(garment.toLowerCase(Locale.ROOT))) issues.add("Wrong garment");
            if (!criteria.path("maxPriceVndPerMeter").isNumber()
                    || criteria.path("maxPriceVndPerMeter").decimalValue().compareTo(expected.path("budget").decimalValue()) != 0)
                issues.add("Wrong per-meter budget");
            if (!criteria.path("budgetInclusive").isBoolean()
                    || criteria.path("budgetInclusive").asBoolean() != expected.path("inclusive").asBoolean())
                issues.add("Wrong budget boundary");
            for (String field : List.of("preferences", "requiredProperties")) {
                if (!strings(expected.path(field)).equals(strings(criteria.path(field)))) issues.add("Wrong " + field);
            }
        }
        JsonNode recommendations = actual.path("recommendations");
        if (!recommendations.isArray()) issues.add("Missing recommendations array");
        Set<String> seen = new HashSet<>(), allowed = strings(expected.path("allowedIds"));
        for (JsonNode recommendation : recommendations) {
            JsonNode fabric = recommendation.path("fabric");
            String id = fabric.path("fabric_id").asText();
            if (!seen.add(id) || !allowed.contains(id) || !catalog.containsKey(id)) {
                issues.add("Invalid/duplicate recommendation ID: " + id);
                continue;
            }
            JsonNode stored = catalog.get(id);
            stored.properties().forEach(entry -> {
                JsonNode returned = fabric.path(entry.getKey()), source = entry.getValue();
                boolean equal = source.isNumber() && returned.isNumber()
                        ? source.decimalValue().compareTo(returned.decimalValue()) == 0 : source.equals(returned);
                if (!equal) issues.add("Catalog field changed: " + id + "/" + entry.getKey());
            });
            if (!recommendation.path("confidence").isNull()) issues.add("Uncalibrated confidence must be null");
        }
        if (status.equals("success")) {
            if (seen.isEmpty()) issues.add("Empty successful answer");
            if (!actual.at("/factCheck/validated").asBoolean()
                    || !actual.at("/factCheck/evaluationStatus").asText().equals("passed"))
                issues.add("Success without passed evaluation");
        } else {
            if (!recommendations.isEmpty()) issues.add("Unexpected recommendations before confirmation");
            if (actual.at("/factCheck/validated").asBoolean()) issues.add("Early return marked validated");
        }
        if (status.equals("insufficient_data")) {
            if (!strings(actual.path("missingPreferences")).equals(strings(expected.path("missing"))))
                issues.add("Wrong missing properties");
            if (actual.path("candidates").isEmpty()) issues.add("Missing unconfirmed candidates");
            for (JsonNode candidate : actual.path("candidates")) {
                if (!allowed.contains(candidate.path("fabric").path("fabric_id").asText()))
                    issues.add("Unexpected candidate ID");
            }
        }
        if (!actual.at("/timings/scope").asText().equals("advice_service")
                || !actual.at("/timings/totalMs").isNumber()) issues.add("Missing service timings");
        return issues;
    }

    private static Set<String> strings(JsonNode array) {
        Set<String> values = new HashSet<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }
}
