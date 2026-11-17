package com.fabric.fabricaiadvisor.service;

import com.fabric.fabricaiadvisor.dto.FabricCriteria;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class FabricDraftClaimRulesTest {
    private final AtomicInteger calls = new AtomicInteger();
    private final FabricValidationService validator = new FabricValidationService(new ObjectMapper(), new AiModelClient() {
        public String getModelName() { return "always-passes"; }
        public String generateJson(String system, String input) { calls.incrementAndGet(); return "{\"passed\":true,\"issues\":[]}"; }
    });
    private final FabricCriteria criteria = new FabricCriteria("recommend_fabric", "sơ mi", new BigDecimal("200000"),
            true, List.of(), false, null, List.of());

    private List<Map<String, Object>> source(String properties) {
        return List.of(Map.of("fabric_id", "F1", "currency", "VND", "price_min_vnd_per_m", 100,
                "price_max_vnd_per_m", 200, "recommended_garments", "Áo sơ mi", "properties_and_cautions", properties,
                "verification_status", "unverified"));
    }
    private FabricExplanationService.Draft draft(String reason) {
        return new FabricExplanationService.Draft("Theo catalog, chưa được xác minh độc lập.", Map.of("F1", reason));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Thoáng khí; thấm hút|Dữ liệu khẳng định vải này mềm mại.",
            "Thoáng; dễ nhăn|Catalog khẳng định vải ít nhăn.",
            "Thoáng; dễ nhăn|Vải chống nhăn rất tốt.",
            "Thoáng khí|Vải không thoáng.",
            "Co giãn tùy cấu trúc|Vải chắc chắn co giãn.",
            "Mềm; chưa xác minh co giãn|Vải co giãn.",
            "Chưa xác minh độ mềm|Vải mềm mại. Dữ liệu chưa xác minh độc lập.",
            "Chưa xác minh độ mềm|Catalog khẳng định vải chắc chắn đáp ứng sở thích mềm."
    })
    void unsupportedOrOppositeClaimsAreRejectedEvenIfTheModelWouldPass(String source, String reason) {
        var result = validator.validate("Sơ mi không quá 200000 đồng/m", criteria, source(source), draft(reason));
        assertFalse(result.rulesPassed());
        assertFalse(result.validated());
        assertEquals("not_run", result.evaluationStatus());
        assertEquals(0, calls.get());
        assertFalse(result.issues().isEmpty());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Thoáng khí; thấm hút|Thoáng khí và thấm hút theo mô tả.",
            "Thoáng; dễ nhăn|Thoáng; dễ nhăn theo catalog.",
            "Co giãn tùy cấu trúc|Co giãn tùy cấu trúc, cần kiểm tra theo mã.",
            "Chưa xác minh độ mềm|Chưa đủ bằng chứng xác nhận độ mềm.",
            "Không co giãn|Không co giãn theo dữ liệu.",
            "Mềm|Mềm theo dữ liệu; chưa xác minh độc lập.",
            "Ít nhăn tùy tỷ lệ pha|Ít nhăn, tùy tỷ lệ pha.",
            "Co giãn, tùy cấu trúc|Co giãn tùy cấu trúc.",
            "Thoáng; dễ nhăn|Dễ nhăn, cần cân nhắc nếu ưu tiên ít nhăn.",
            "Thoáng; dễ nhăn|Dễ nhăn, không đáp ứng sở thích ít nhăn."
    })
    void supportedAndExplicitlyUnknownClaimsRemainEligibleForModelAssessment(String source, String reason) {
        assertTrue(validator.validate("Sơ mi", criteria, source(source), draft(reason)).validated());
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @CsvSource(delimiter='|', value={
            "Ít nhăn hơn cotton thuần tùy tỷ lệ pha|Kate dùng cho sơ mi; chắc chắn không nhăn với mọi tỷ lệ pha.",
            "Ít nhăn|Vải không nhăn.",
            "Không nhăn; dễ nhăn|Vải chắc chắn không nhăn.",
            "Chống nhăn|Vải hoàn toàn không bị nhăn.",
            "Thoáng; dễ nhăn|Vải không bao giờ nhăn.",
            "Chưa có dữ liệu về độ nhăn|Catalog khẳng định vải không hề nhăn.",
            "Không nhăn tùy xử lý hoàn tất|Vải chắc chắn không nhăn.",
            "Ít nhăn tùy tỷ lệ pha|Vải không nhăn. Dữ liệu chưa xác minh độc lập."
    })
    void absoluteWrinkleClaimsNeedExplicitEvidenceEvenIfTheEvaluatorWouldPass(String properties, String reason) {
        var sources = source(properties);
        var draft = draft(reason);
        assertFalse(validator.validate("Sơ mi", criteria, sources, draft).rulesPassed());
        assertEquals(0, calls.get());
        assertFalse(validator.rulesOnly(criteria, sources, draft, "offline").rulesPassed());
        // Model-only evaluation must continue to expose a bad model verdict independently.
        assertTrue(validator.evaluateModel("Sơ mi", criteria, sources, draft).validated());
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @CsvSource(delimiter='|', value={
            "Không nhăn|Không nhăn theo catalog.",
            "Không nhăn; chưa xác minh co giãn|Không nhăn theo catalog.",
            "Không bị nhăn|Vải không bị nhăn theo mô tả.",
            "Không bao giờ nhăn|Vải không nhăn theo catalog.",
            "Ít nhăn|Không dễ nhăn theo catalog.",
            "Ít nhăn tùy tỷ lệ pha|Ít nhăn hơn cotton tùy tỷ lệ pha.",
            "Ít nhăn|Chưa đủ bằng chứng để khẳng định vải không nhăn.",
            "Ít nhăn|Cần kiểm tra xem vải có không nhăn hay không.",
            "Dễ nhăn|Không khẳng định vải không nhăn.",
            "Dễ nhăn|Không phải là không nhăn.",
            "Dễ nhăn|Yêu cầu của người mua là không nhăn."
    })
    void supportedRelativeWrinkleClaimsAndExplicitUnknownsRemainEligible(String properties, String reason) {
        assertTrue(validator.validate("Sơ mi", criteria, source(properties), draft(reason)).validated());
        assertEquals(1, calls.get());
    }

    @Test
    void anAbsoluteClaimCannotBorrowAnotherFabricsEvidenceAndSummaryAndFallbackAreChecked() {
        var first = source("Ít nhăn tùy tỷ lệ pha").getFirst();
        var second = new LinkedHashMap<String,Object>(source("Không nhăn").getFirst());
        second.put("fabric_id", "F2");
        var reasons = Map.of("F1", "Vải chắc chắn không nhăn.", "F2", "Không nhăn theo catalog.");
        assertFalse(validator.validate("Sơ mi", criteria, List.of(first, second),
                new FabricExplanationService.Draft("Theo catalog.", reasons)).rulesPassed());
        var summary = new FabricExplanationService.Draft("Vải không nhăn.", Map.of("F1", "Theo catalog."));
        assertFalse(validator.validate("Sơ mi", criteria, List.of(first), summary).rulesPassed());
        assertFalse(validator.rulesOnly(criteria, List.of(first), summary, "offline").rulesPassed());
        assertEquals(0, calls.get());
    }

    @Test
    void summaryAssertionsAlsoNeedSupportAndFallbackUsesTheSameChecks() {
        var bad = new FabricExplanationService.Draft("Vải này mềm mại.", Map.of("F1", "Thoáng theo mô tả."));
        assertFalse(validator.validate("Sơ mi", criteria, source("Thoáng khí"), bad).validated());
        assertFalse(validator.rulesOnly(criteria, source("Thoáng khí"), bad, "offline").rulesPassed());
        assertEquals(0, calls.get());
    }

    @Test
    void independentEvaluatorBenchmarkDoesNotCountBackendRejectionAsModelAccuracy() {
        var sources = source("Thoáng khí; thấm hút");
        var bad = draft("Catalog khẳng định mềm mại.");
        assertFalse(validator.validate("Sơ mi", criteria, sources, bad).validated());
        assertEquals(0, calls.get());
        // The deliberately bad evaluator still returns its real (wrong) verdict in model-only mode.
        assertTrue(validator.evaluateModel("Sơ mi", criteria, sources, bad).validated());
        assertEquals(1, calls.get());
    }
}
