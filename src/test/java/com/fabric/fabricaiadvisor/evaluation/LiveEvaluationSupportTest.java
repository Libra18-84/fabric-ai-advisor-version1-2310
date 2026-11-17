package com.fabric.fabricaiadvisor.evaluation;

import org.junit.jupiter.api.Test;
import com.fabric.fabricaiadvisor.dto.FabricCriteria;
import com.fabric.fabricaiadvisor.service.AiModelClient;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LiveEvaluationSupportTest {
    private static JsonNode guardRejection() {
        return JSON.readTree("""
                {"status":"validation_failed","criteria":{"garment":"sơ mi","maxPriceVndPerMeter":90000,
                 "budgetInclusive":false,"preferences":[],"requiredProperties":[]},"recommendations":[],
                 "factCheck":{"rulesPassed":false,"validated":false,"evaluationStatus":"not_run","issues":["Wrong price"]},
                 "timings":{"scope":"advice_service","totalMs":123}}
                """);
    }

    @Test
    void aGeneratedDraftRejectedByRulesIsMeasuredButStillFailsTheAdviceContract() {
        var body = guardRejection();
        assertTrue(LiveEvaluationSupport.usableResponse(200, body, true));
        assertFalse(LiveEvaluationSupport.adviceIssues(expected(), body, catalog()).isEmpty());
        assertTrue(LiveEvaluationSupport.adviceIssues(expected(), body, catalog()).contains("Unexpected status"));
    }

    @Test
    void missingOrMalformedGenerationCannotBeMistakenForAHealthyRulesRejection() {
        assertFalse(LiveEvaluationSupport.usableResponse(200, guardRejection(), false));
        var empty = (tools.jackson.databind.node.ObjectNode) guardRejection();
        ((tools.jackson.databind.node.ObjectNode) empty.path("factCheck")).putArray("issues");
        assertFalse(LiveEvaluationSupport.usableResponse(200, empty, true));
    }

    @Test
    void transportInvalidEvaluationAndFallbackStillStopTheLiveRun() {
        assertFalse(LiveEvaluationSupport.usableResponse(503, guardRejection(), true));
        var nullCriteria = (tools.jackson.databind.node.ObjectNode) guardRejection(); nullCriteria.putNull("criteria");
        assertFalse(LiveEvaluationSupport.usableResponse(200, nullCriteria, true));
        for (String evaluation : List.of("invalid_response", "unavailable")) {
            var body = (tools.jackson.databind.node.ObjectNode) guardRejection();
            ((tools.jackson.databind.node.ObjectNode) body.path("factCheck")).put("evaluationStatus", evaluation);
            assertFalse(LiveEvaluationSupport.usableResponse(200, body, true));
        }
        var fallback = (tools.jackson.databind.node.ObjectNode) guardRejection(); fallback.put("status", "fallback");
        assertFalse(LiveEvaluationSupport.usableResponse(200, fallback, true));
    }

    @Test
    void anIndependentModelRejectionIsAlsoAUsableMeasuredResult() {
        var body = (tools.jackson.databind.node.ObjectNode) guardRejection();
        var check = (tools.jackson.databind.node.ObjectNode) body.path("factCheck");
        check.put("rulesPassed", true); check.put("evaluationStatus", "failed");
        assertTrue(LiveEvaluationSupport.usableResponse(200, body, true));
    }

    @Test
    void diagnosticCaptureCallsTheMainModelOncePerAttemptAndKeepsCorrectionIssues() {
        AtomicInteger calls = new AtomicInteger();
        var capture = new LiveAiEvaluationIT.CapturingExplanations(new AiModelClient() {
            public String getModelName() { return "fake-main"; }
            public String generateJson(String prompt, String input) {
                calls.incrementAndGet();
                return "{\"explanation\":\"Theo catalog.\",\"recommendations\":[{\"fabricId\":\"FAB001\",\"reason\":\"Thoáng khí.\"}]}";
            }
        }, JSON);
        var criteria = new FabricCriteria("recommend_fabric", "sơ mi", new BigDecimal("200000"), true, List.of(), false, null);
        var rows = List.<Map<String,Object>>of(Map.of("fabric_id", "FAB001"));
        var first = capture.generate("Sơ mi", criteria, rows);
        capture.correct("Sơ mi", criteria, rows, first, List.of("Wrong price"));
        var second = capture.generate("Sơ mi", criteria, rows, Map.of("context", "casual"));
        capture.correct("Sơ mi", criteria, rows, second, List.of("Wrong price"), Map.of("context", "casual"));
        assertEquals(4, calls.get()); assertEquals(4, capture.snapshot().size());
        assertEquals(List.of("generate", "correct", "generate", "correct"), capture.snapshot().stream().map(e -> e.get("operation")).toList());
        var serialized = JSON.valueToTree(capture.snapshot());
        assertEquals("Thoáng khí.", serialized.get(0).at("/draft/reasonsById/FAB001").asText());
        assertEquals("Wrong price", serialized.get(1).path("validationIssues").get(0).asText());
        assertEquals(first.explanation(), serialized.get(1).at("/previousDraft/explanation").asText());
        capture.clear(); assertTrue(capture.snapshot().isEmpty());
    }

    @Test
    void aFailedGenerationIsRecordedWithoutInventingACompletedDraft() {
        var capture = new LiveAiEvaluationIT.CapturingExplanations(new AiModelClient() {
            public String getModelName() { return "fake-main"; }
            public String generateJson(String prompt, String input) { return "not json"; }
        }, JSON);
        var criteria = new FabricCriteria("recommend_fabric", "sơ mi", BigDecimal.ONE, true, List.of(), false, null);
        assertThrows(IllegalStateException.class, () -> capture.generate("Sơ mi", criteria, List.of(Map.of("fabric_id", "FAB001"))));
        assertEquals(1, capture.snapshot().size());
        assertTrue(capture.snapshot().getFirst().containsKey("error"));
        assertFalse(capture.snapshot().getFirst().containsKey("draft"));
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void quantilesUseNearestRankAndDoNotInventEmptyMeasurements() {
        assertNull(LiveEvaluationSupport.latency(List.of()).get("p95"));
        var result = LiveEvaluationSupport.latency(List.of(10.0, 1.0, 7.0, 4.0));
        assertEquals(4.0, result.get("p50"));
        assertEquals(10.0, result.get("p95"));
        assertEquals(10.0, result.get("p99"));
        assertEquals(1.0, LiveEvaluationSupport.latency(List.of(1.0)).get("p50"));
    }

    @Test
    void evaluatorTransportErrorsAndBackendRejectionsAreNotCorrectNegativePredictions() {
        var result = LiveEvaluationSupport.quality(List.of(
                new LiveEvaluationSupport.Judgement(false, true, "unavailable"),
                new LiveEvaluationSupport.Judgement(false, true, "invalid_response"),
                new LiveEvaluationSupport.Judgement(false, false, "not_run"),
                new LiveEvaluationSupport.Judgement(true, true, "passed"),
                new LiveEvaluationSupport.Judgement(false, true, "failed")));
        assertEquals(2L, result.get("classified"));
        assertEquals(3L, result.get("unclassified"));
        assertEquals(1.0, result.get("agreementOnClassified"));
        assertEquals(0.4, result.get("agreementOnAllCases"));
        assertEquals(0.4, result.get("coverage"));
        assertNull(LiveEvaluationSupport.quality(List.of()).get("agreementOnClassified"));
    }

    @Test
    void evaluatorConfusionMatrixDistinguishesFalsePassAndFalseRejection() {
        var result = LiveEvaluationSupport.quality(List.of(
                new LiveEvaluationSupport.Judgement(true, true, "passed"),
                new LiveEvaluationSupport.Judgement(false, true, "failed"),
                new LiveEvaluationSupport.Judgement(false, true, "passed"),
                new LiveEvaluationSupport.Judgement(true, true, "failed")));
        for (String key : List.of("truePositive", "trueNegative", "falsePositive", "falseNegative"))
            assertEquals(1L, result.get(key), key);
        assertEquals(0.5, result.get("agreementOnAllCases"));
    }

    private static JsonNode expected() {
        return JSON.readTree("""
                {"status":"success","allowedIds":["FAB001"],"garment":"sơ mi",
                 "budget":90000,"inclusive":false,"preferences":[],"requiredProperties":[]}
                """);
    }

    private static JsonNode answer() {
        return JSON.readTree("""
                {"status":"success","criteria":{"garment":"sơ mi","maxPriceVndPerMeter":90000,
                 "budgetInclusive":false,"preferences":[],"requiredProperties":[]},
                 "recommendations":[{"fabric":{"fabric_id":"FAB001","price_max_vnd_per_m":55000,
                 "verification_status":"unverified"},"confidence":null}],
                 "factCheck":{"validated":true,"evaluationStatus":"passed"},
                 "timings":{"scope":"advice_service","totalMs":123}}
                """);
    }

    private static Map<String, JsonNode> catalog() {
        return Map.of("FAB001", JSON.readTree("""
                {"fabric_id":"FAB001","price_max_vnd_per_m":55000.0,"verification_status":"unverified"}
                """));
    }

    @Test
    void contractAllowsSelectedSubsetAndEquivalentSqlNumberTypes() {
        assertTrue(LiveEvaluationSupport.adviceIssues(expected(), answer(), catalog()).isEmpty());
    }

    @Test
    void contractDetectsLostNegationBoundarySourceMutationAndUncalibratedConfidence() {
        var answer = (tools.jackson.databind.node.ObjectNode) answer();
        ((tools.jackson.databind.node.ObjectNode) answer.path("criteria")).put("budgetInclusive", true);
        ((tools.jackson.databind.node.ObjectNode) answer.path("criteria")).putArray("requiredProperties").add("co giãn");
        var row = (tools.jackson.databind.node.ObjectNode) answer.path("recommendations").get(0);
        row.put("confidence", 0.99);
        ((tools.jackson.databind.node.ObjectNode) row.path("fabric")).put("verification_status", "verified");
        var issues = LiveEvaluationSupport.adviceIssues(expected(), answer, catalog());
        assertTrue(issues.contains("Wrong budget boundary"));
        assertTrue(issues.contains("Wrong requiredProperties"));
        assertTrue(issues.contains("Catalog field changed: FAB001/verification_status"));
        assertTrue(issues.contains("Uncalibrated confidence must be null"));
    }
}
