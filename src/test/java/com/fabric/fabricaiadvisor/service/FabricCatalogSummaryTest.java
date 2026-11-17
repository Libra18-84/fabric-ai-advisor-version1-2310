package com.fabric.fabricaiadvisor.service;

import com.fabric.fabricaiadvisor.dto.FabricCriteria;
import org.junit.jupiter.api.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

class FabricCatalogSummaryTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static JsonNode fixtures() throws Exception {
        try (var input = FabricCatalogSummaryTest.class.getResourceAsStream("/evaluation/captured-advice-regressions.json")) {
            return JSON.readTree(Objects.requireNonNull(input).readAllBytes());
        }
    }
    private static List<Map<String,Object>> sources(JsonNode set) {
        return JSON.convertValue(set.path("catalog"), new TypeReference<>() {});
    }
    private static FabricExplanationService.Draft draft(JsonNode node) {
        return JSON.treeToValue(node, FabricExplanationService.Draft.class);
    }

    @TestFactory
    Stream<DynamicTest> actualCapturedDraftsKeepTheirReasonsAndUseTheCatalogSummary() throws Exception {
        var set = fixtures(); var sources = sources(set);
        return StreamSupport.stream(set.path("cases").spliterator(), false).flatMap(c ->
                IntStream.range(0, c.path("drafts").size()).mapToObj(i -> DynamicTest.dynamicTest(c.path("id").asText()+"-"+(i+1), () -> {
                    var original = draft(c.path("drafts").get(i));
                    var rendered = FabricCatalogSummary.apply(sources, original);
                    AtomicInteger calls = new AtomicInteger();
                    var validator = new FabricValidationService(JSON, new AiModelClient() {
                        public String getModelName() { return "fake-evaluator"; }
                        public String generateJson(String prompt, String input) {
                            calls.incrementAndGet();
                            assertEquals(rendered.explanation(), JSON.readTree(input).at("/draft/explanation").asText());
                            return "{\"passed\":true,\"issues\":[]}";
                        }
                    });
                    var result = validator.validate(c.path("query").asText(), JSON.treeToValue(c.path("criteria"), FabricCriteria.class), sources, rendered);
                    assertTrue(result.validated(), result.issues().toString());
                    assertEquals(1, calls.get());
                    assertEquals(original.reasonsById(), rendered.reasonsById());
                    assertFalse(rendered.explanation().contains("45.000"));
                    assertFalse(rendered.explanation().contains("100%"));
                })));
    }

    @Test
    void changingTheSummaryNeverRepairsOrApprovesAFabricatedPerFabricReason() throws Exception {
        var set = fixtures(); var sources = sources(set); var c = set.path("cases").get(0);
        var original = new FabricExplanationService.Draft("Tóm tắt tùy ý.", Map.of("FAB001", "Giá catalog là 10.000–20.000 VND/m."));
        var rendered = FabricCatalogSummary.apply(sources, original);
        AtomicInteger calls = new AtomicInteger();
        var validator = new FabricValidationService(JSON, new AiModelClient() {
            public String getModelName() { return "always-passes"; }
            public String generateJson(String prompt, String input) { calls.incrementAndGet(); return "{\"passed\":true,\"issues\":[]}"; }
        });
        assertFalse(validator.validate(c.path("query").asText(), JSON.treeToValue(c.path("criteria"), FabricCriteria.class), sources, rendered).validated());
        assertEquals(0, calls.get()); assertEquals(original.reasonsById(), rendered.reasonsById());
    }

    @Test
    void countAndVerificationNoticeReferOnlyToTheSelectedSubset() throws Exception {
        var sources = sources(fixtures());
        sources.getFirst().put("verification_status", "independently_verified");
        var rendered = FabricCatalogSummary.apply(sources, new FabricExplanationService.Draft("Ignored.", Map.of("FAB001", "Theo catalog.")));
        assertTrue(rendered.explanation().contains("1 loại vải"));
        assertFalse(rendered.explanation().contains("Có dữ liệu chưa được xác minh"));
        var unconfirmed = FabricCatalogSummary.apply(sources, new FabricExplanationService.Draft("Ignored.", Map.of("FAB005", "Theo catalog.")));
        assertTrue(unconfirmed.explanation().contains("Có dữ liệu chưa được xác minh"));
    }

    @Test
    void unknownOrMissingSelectionsAreNeverInventedOrSilentlyDropped() throws Exception {
        var sources = sources(fixtures());
        assertThrows(IllegalStateException.class, () -> FabricCatalogSummary.apply(sources, new FabricExplanationService.Draft("Ignored.", Map.of("OUTSIDE", "Fake."))));
        assertThrows(IllegalStateException.class, () -> FabricCatalogSummary.apply(sources, new FabricExplanationService.Draft("Ignored.", Map.of())));
    }
}
