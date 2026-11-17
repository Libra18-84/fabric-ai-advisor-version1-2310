package com.fabric.fabricaiadvisor.service;

import com.fabric.fabricaiadvisor.dto.FabricCriteria;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

class FabricDraftFactRulesTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final FabricCriteria CRITERIA = new FabricCriteria("recommend_fabric", "sơ mi", new BigDecimal("200000"),
            true, List.of(), false, null, List.of());
    private final AtomicInteger modelCalls = new AtomicInteger();
    private final FabricValidationService validator = new FabricValidationService(JSON, new AiModelClient() {
        public String getModelName() { return "deliberately-always-passes"; }
        public String generateJson(String prompt, String input) { modelCalls.incrementAndGet(); return "{\"passed\":true,\"issues\":[]}"; }
    });

    private static Map<String, Object> cotton() {
        return new LinkedHashMap<>(Map.ofEntries(Map.entry("fabric_id", "FAB001"), Map.entry("fabric_name", "Cotton 100% woven"),
                Map.entry("currency", "VND"), Map.entry("price_min_vnd_per_m", 45000), Map.entry("price_max_vnd_per_m", 90000),
                Map.entry("recommended_garments", "Áo sơ mi, váy, đồ trẻ em"), Map.entry("properties_and_cautions", "Thoáng khí, thấm hút; có thể co sau giặt"),
                Map.entry("fiber_composition", "Cotton 100%"), Map.entry("verification_status", "unverified"),
                Map.entry("source_url", "https://www.vaithaihoa.vn/tin-tuc/gia-vai-si-2026")));
    }
    private static Map<String, Object> kate() {
        var row = cotton(); row.putAll(Map.of("fabric_id", "FAB005", "fabric_name", "Kate / cotton-poly blend", "fiber_composition", "Polyester/cotton; tỷ lệ tùy mã",
                "price_min_vnd_per_m", 30000, "price_max_vnd_per_m", 55000, "properties_and_cautions", "Ít nhăn hơn cotton thuần tùy tỷ lệ pha",
                "source_url", "https://www.vaithaihoa.vn/tin-tuc/bang-gia-vai-si-thai-hoa"));
        return row;
    }
    private static Map<String, Object> linen() {
        var row = cotton(); row.putAll(Map.of("fabric_id", "FAB006", "fabric_name", "Linen / đũi", "fiber_composition", "Linen hoặc blend; phải xác minh nhãn",
                "price_min_vnd_per_m", 85000, "price_max_vnd_per_m", 160000, "properties_and_cautions", "Thoáng; dễ nhăn, độ rũ phụ thuộc thành phần"));
        return row;
    }
    private static FabricExplanationService.Draft draft(String reason) {
        return new FabricExplanationService.Draft("Theo catalog, dữ liệu chưa xác minh độc lập.", Map.of("FAB001", reason));
    }

    @ParameterizedTest
    @CsvSource(delimiter='|', value={
            "Giá catalog là 10.000–20.000 VND/m.", "Giá từ 45.000 đến 95.000 đồng/m.",
            "Giá 45–95k đồng/m.", "Giá từ 45000.0 đến 95000.0 VND/m.",
            "Giá cố định 45.000 đồng/m.", "Giá 90.000 VND/m.", "Giá 45.000–90.000 USD/m.",
            "Giá 45.000–90.000 VND/kg.", "Giới hạn giá yêu cầu 100.000 đồng/m.",
            "Giá 45.000–90.000 VND/m².", "Giá 45.000–90.000 VND/m2.",
            "Giá 45.000–90.000 VND/mét vuông.", "Tổng chi phí 45.000–90.000 VND.",
            "Giá 45.000–90.000 VND/yard.",
            "Giá 45.000–90.000 VND cho mỗi kg.",
            "Giá từ -45.000 đến 90.000 VND/m.", "Giá âm 45.000 VND/m.",
            "Thành phần theo catalog là 100% polyester.", "Thành phần là polyester.", "Cotton 90%.",
            "Thành phần là 80% bông và 20% polyester.",
            "Dữ liệu đã được xác minh độc lập.", "Chưa xác minh, nhưng nguồn đã được xác minh độc lập.",
            "Dữ liệu is independently verified.",
            "Nguồn catalog là https://catalog.example.invalid/verified-cotton.",
            "Nguồn là https://www.vaithaihoa.vn/tin-tuc/bang-gia-vai-si-thai-hoa."
    })
    void explicitFalseFactsStopBeforeAnAlwaysPassingModel(String reason) {
        var result = validator.validate("Sơ mi không quá 200.000 đồng/m", CRITERIA, List.of(cotton(), kate()), draft(reason));
        assertFalse(result.rulesPassed(), reason); assertFalse(result.validated());
        assertEquals("not_run", result.evaluationStatus()); assertEquals(0, modelCalls.get());
        assertFalse(result.issues().isEmpty());
    }

    @ParameterizedTest
    @CsvSource(delimiter='|', value={
            "Giá từ 45.000 đến 90.000 VND/m.", "Giá 45000.0–90000.0 đồng/mét.",
            "Giá 45–90k đồng/m.", "Giá từ 45 nghìn đến 90 nghìn đồng/m.",
            "Giá từ 45.000 đồng đến 90.000 đồng/m.", "Giá từ 45.000 VND/m.", "Giá tối đa 90.000 VND/m.",
            "Giá từ 45000 VND đến 90000 VND.", "Giá 45.000–90.000 VND.",
            "Theo ngân sách không quá 200.000 đồng/m.", "Yêu cầu về giá tối đa 200.000 VND/mét.",
            "Thành phần là 100% cotton.", "Thành phần là bông 100%.", "Thành phần là cotton.",
            "Chưa được xác minh độc lập.", "Cần được xác minh độc lập.", "Nguồn chưa xác minh; cần kiểm tra trước khi mua.",
            "Nguồn: https://www.vaithaihoa.vn/tin-tuc/gia-vai-si-2026.",
            "Chưa đủ thông tin về giá bán chính xác; backend hiển thị khoảng giá."
    })
    void supportedFactsAndUncertaintyStillReachTheModel(String reason) {
        assertTrue(validator.validate("Sơ mi", CRITERIA, List.of(cotton()), draft(reason)).validated(), reason);
        assertEquals(1, modelCalls.get());
    }

    @Test
    void wrongFactsInTheSummaryAreAlsoRejectedAndCannotBorrowFromAnotherFabric() {
        var reasons = Map.of("FAB001", "Thoáng khí và thấm hút; có thể co sau giặt.", "FAB005", "Ít nhăn hơn cotton thuần tùy tỷ lệ pha.");
        var wrong = new FabricExplanationService.Draft("Cotton có giá 30.000–55.000 VND/m.", reasons);
        assertFalse(validator.validate("Sơ mi", CRITERIA, List.of(cotton(), kate()), wrong).validated());
        assertEquals(0, modelCalls.get());
        var correct = new FabricExplanationService.Draft("Cotton có giá 45.000–90.000 VND/m. Kate có giá 30.000–55.000 VND/m.", reasons);
        assertTrue(validator.validate("Sơ mi", CRITERIA, List.of(cotton(), kate()), correct).validated());
    }

    @Test
    void actualSqlPriceChangesAreUsedAndFallbackMustAlsoPassTheFactGuard() {
        var row = cotton(); row.put("price_max_vnd_per_m", 95000);
        var changed = draft("Giá catalog là 45.000–95.000 VND/m.");
        assertTrue(validator.validate("Sơ mi", CRITERIA, List.of(row), changed).validated());
        assertFalse(validator.rulesOnly(CRITERIA, List.of(row), draft("Giá 45.000–90.000 VND/m."), "offline").rulesPassed());
        assertEquals(1, modelCalls.get());
    }

    @ParameterizedTest
    @CsvSource(delimiter='|', value={
            "Đề xuất Cotton 100% woven, Kate / cotton-poly blend và Linen / đũi theo catalog, chưa xác minh độc lập.",
            "Cotton có giá 45.000–90.000 VND/m, Kate có giá 30.000–55.000 VND/m, Linen có giá 85.000–160.000 VND/m.",
            "FAB001 có giá 45.000–90.000 VND/m, FAB005 có giá 30.000–55.000 VND/m, FAB006 có giá 85.000–160.000 VND/m.",
            "Cotton 100% woven có giá 45.000–90.000 VND/m, Kate/cotton-poly blend có giá 30.000–55.000 VND/m.",
            "Các loại vải được chọn có khoảng giá 30.000–160.000 VND/m theo catalog.",
            "Cotton, Kate và Linen có khoảng giá 30.000–160.000 VND/m theo catalog.",
            "Cotton có thành phần là 100% cotton, Kate có tỷ lệ tùy mã theo catalog."
    })
    void factsInOneSummaryRemainAttachedToTheirOwnFabric(String summary) {
        var reasons = Map.of("FAB001", "Thoáng khí, thấm hút; có thể co sau giặt.",
                "FAB005", "Ít nhăn hơn cotton thuần tùy tỷ lệ pha.", "FAB006", "Thoáng; dễ nhăn, độ rũ phụ thuộc thành phần.");
        var draft = new FabricExplanationService.Draft(summary, reasons);
        assertTrue(validator.validate("Sơ mi", CRITERIA, List.of(cotton(), kate(), linen()), draft).validated());
        assertEquals(1, modelCalls.get());
    }

    @ParameterizedTest
    @CsvSource(delimiter='|', value={
            "Cotton có giá 30.000–55.000 VND/m, Kate có giá 45.000–90.000 VND/m.",
            "FAB001 có giá 30.000–55.000 VND/m, FAB005 có giá 45.000–90.000 VND/m.",
            "Kate có thành phần là 100% cotton.",
            "Kate và Cotton đều có thành phần là 100% cotton.",
            "Cotton và Kate có cùng khoảng giá 30.000–55.000 VND/m.",
            "Các loại vải được chọn có khoảng giá 30.000–200.000 VND/m."
    })
    void multipleFabricMentionsCannotHideSwappedFactsOrWrongAggregateBounds(String summary) {
        var draft = new FabricExplanationService.Draft(summary, Map.of("FAB001", "Thoáng khí; có thể co sau giặt.",
                "FAB005", "Ít nhăn hơn cotton thuần tùy tỷ lệ pha.", "FAB006", "Thoáng; dễ nhăn."));
        assertFalse(validator.validate("Sơ mi", CRITERIA, List.of(cotton(), kate(), linen()), draft).validated());
        assertEquals(0, modelCalls.get());
    }

    @Test
    void genuineSinglePricesAndUrlQueryStringsArePreserved() {
        var row = cotton(); row.put("price_min_vnd_per_m", 90000);
        row.put("source_url", "https://catalog.test/item?id=1&lang=vi");
        assertTrue(validator.validate("Sơ mi", CRITERIA, List.of(row), draft("Giá 90.000 đồng/m. Nguồn: https://catalog.test/item?id=1&lang=vi")).validated());
    }

    @Test
    void aSharedFabricNameCannotSilentlySelectTheFirstCatalogRow() {
        var second = kate(); second.put("fabric_name", "Cotton poplin");
        var reasons = Map.of("FAB001", "Thoáng khí; có thể co sau giặt.", "FAB005", "Ít nhăn hơn cotton thuần tùy tỷ lệ pha.");
        for (var rows : List.of(List.of(cotton(), second), List.of(second, cotton()))) {
            var ambiguous = new FabricExplanationService.Draft("Cotton có giá 45.000–90.000 VND/m.", reasons);
            assertFalse(validator.validate("Sơ mi", CRITERIA, rows, ambiguous).validated());
            var explicit = new FabricExplanationService.Draft("FAB001 có giá 45.000–90.000 VND/m, FAB005 có giá 30.000–55.000 VND/m.", reasons);
            assertTrue(validator.validate("Sơ mi", CRITERIA, rows, explicit).validated());
        }
    }

    @Test
    void verificationFlagsAreNotPromotedToIndependentVerification() {
        var row = cotton(); row.put("verification_status", "verified");
        assertTrue(validator.validate("Sơ mi", CRITERIA, List.of(row), draft("Theo catalog, dữ liệu đã được xác minh.")).validated());
        assertFalse(validator.validate("Sơ mi", CRITERIA, List.of(row), draft("Dữ liệu đã được xác minh độc lập.")).validated());
        assertEquals(1, modelCalls.get());
    }

    @TestFactory
    Stream<DynamicTest> actualEvaluatorExamplesAreProtectedWithoutCreditingTheModel() throws Exception {
        JsonNode dataset;
        try (var input = getClass().getResourceAsStream("/evaluation/evaluator-cases.json")) {
            dataset = JSON.readTree(Objects.requireNonNull(input).readAllBytes());
        }
        return StreamSupport.stream(dataset.path("cases").spliterator(), false).map(item -> DynamicTest.dynamicTest(item.path("id").asText(), () -> {
            var reasons = new LinkedHashMap<String, String>();
            item.path("reasonsById").properties().forEach(e -> reasons.put(e.getKey(), e.getValue().asText()));
            var draft = new FabricExplanationService.Draft(item.path("explanation").asText(), reasons);
            var sources = List.of(cotton(), kate(), linen());
            int before = modelCalls.get();
            var production = validator.validate(item.path("query").asText(), CRITERIA, sources, draft);
            boolean expected = item.path("expectedPass").asBoolean();
            assertEquals(expected, production.validated(), production.issues().toString());
            assertEquals(expected ? before+1 : before, modelCalls.get());
            // A bad independent evaluator remains bad in the model-only benchmark.
            assertTrue(validator.evaluateModel(item.path("query").asText(), CRITERIA, sources, draft).validated());
            assertEquals(expected ? before+2 : before+1, modelCalls.get());
        }));
    }
}
