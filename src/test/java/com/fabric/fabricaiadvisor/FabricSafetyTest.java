package com.fabric.fabricaiadvisor;

import com.fabric.fabricaiadvisor.dto.FabricCriteria;
import com.fabric.fabricaiadvisor.service.*;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FabricSafetyTest {
    private record Case(String description, String request, FabricEvidenceRules.State expected) {}

    @TestFactory
    Stream<DynamicTest> evidenceMustNotInventCertainty() {
        var unknown = FabricEvidenceRules.State.UNKNOWN;
        var match = FabricEvidenceRules.State.MATCH;
        var conflict = FabricEvidenceRules.State.CONFLICT;
        return Stream.of(
                new Case("Chưa xác minh độ mềm", "mềm", unknown),
                new Case("Chưa có dữ liệu về độ mềm", "mềm", unknown),
                new Case("Không có bằng chứng vải mềm", "mềm", unknown),
                new Case("Độ mềm chưa đo", "mềm", unknown),
                new Case("Chưa xác nhận vải mềm", "mềm", unknown),
                new Case("Cần kiểm tra độ mềm", "mềm", unknown),
                new Case("Không rõ độ mềm", "mềm", unknown),
                new Case("Mềm mại; chưa xác minh độ mềm", "mềm", unknown),
                new Case("Có thể mềm", "mềm", unknown),
                new Case("Độ mềm tùy hoàn tất", "mềm", unknown),
                new Case("Mềm; không mềm", "mềm", unknown),
                new Case("Chưa xác minh có co giãn hay không", "không co giãn", unknown),
                new Case("Ít co giãn", "không co giãn", unknown),
                new Case("Dệt thoi", "không co giãn", unknown),
                new Case("Không co giãn", "không co giãn", match),
                new Case("Không mềm", "mềm", conflict),
                new Case("Mềm mại; cần kiểm tra khổ vải", "mềm", match),
                new Case("Cần kiểm tra độ mềm; thoáng khí", "thoáng mát", match),
                new Case("Co giãn", "không co giãn", conflict),
                new Case("Không dễ nhăn", "ít nhăn", match),
                new Case("Thoáng khí", "thoáng", match),
                new Case("Thoáng khí", "mát mẻ", match),
                new Case("Ít nhăn", "hạn chế nhăn", match),
                new Case("Cần kiểm tra độ co giãn", "co giãn", unknown),
                new Case("Chưa xác minh khả năng thấm hút", "thấm hút", unknown)
        ).map(c -> DynamicTest.dynamicTest(c.description() + " / " + c.request(),
                () -> assertEquals(c.expected(), FabricEvidenceRules.evaluate(c.description(), c.request()))));
    }

    private FabricCriteria criteria(List<String> soft, List<String> hard) {
        return new FabricCriteria("recommend_fabric", "sơ mi", new BigDecimal("200000"),
                true, soft, false, null, hard);
    }

    @Test
    void modelCannotReverseAnExplicitNegativeConstraint() {
        var result = FabricConstraintService.guard("Sơ mi không co giãn, tối đa 200000 đồng/m",
                criteria(List.of("co giãn"), List.of()));
        assertTrue(result.needsClarification());
        assertTrue(result.clarificationQuestion().contains("không co giãn"));
    }

    @Test
    void omittedExplicitExclusionBecomesMandatory() {
        var result = FabricConstraintService.guard("Sơ mi không co giãn", criteria(List.of(), List.of()));
        assertEquals(List.of("không co giãn"), result.requiredProperties());
        assertTrue(result.preferences().isEmpty());
    }

    @Test
    void noNeedIsNeutralRatherThanTheOpposite() {
        var result = FabricConstraintService.guard("Không cần co giãn nhưng muốn mềm",
                criteria(List.of("co giãn", "mềm"), List.of("co giãn")));
        assertEquals(List.of("mềm"), result.preferences());
        assertTrue(result.requiredProperties().isEmpty());
        assertFalse(result.needsClarification());
    }

    @Test
    void mandatoryPropertiesAreSeparateFromSoftPreferences() {
        var result = FabricConstraintService.guard("Bắt buộc không co giãn, ưu tiên mềm",
                criteria(List.of("không co giãn", "mềm"), List.of()));
        assertEquals(List.of("không co giãn"), result.requiredProperties());
        assertEquals(List.of("mềm"), result.preferences());
    }

    @Test
    void mandatoryScopeContinuesAcrossAnd() {
        var result = FabricConstraintService.guard("Vải bắt buộc thoáng khí và mềm",
                criteria(List.of("thoáng mát", "mềm"), List.of()));
        assertEquals(Set.of("thoáng mát", "mềm"), new HashSet<>(result.requiredProperties()));
    }

    @Test
    void ambiguousDegreeIsNotChangedToAbsoluteAbsence() {
        assertTrue(FabricConstraintService.guard("Sơ mi ít co giãn",
                criteria(List.of("co giãn"), List.of())).needsClarification());
    }

    @Test
    void missingUnsupportedMandatoryPropertyAsksForClarification() {
        assertTrue(FabricConstraintService.guard("Vải bắt buộc chống cháy",
                criteria(List.of(), List.of())).needsClarification());
    }

    @Test
    void contradictoryOriginalConditionsAskForClarification() {
        assertTrue(FabricConstraintService.guard("Vải mềm nhưng không mềm",
                criteria(List.of("mềm"), List.of())).needsClarification());
    }

    @Test
    void garmentMatchingUsesSynonymsAccentsAndWordBoundaries() {
        assertTrue(FabricText.matchesGarment("Áo sơ mi, váy", "AO_SO_MI"));
        assertTrue(FabricText.matchesGarment("Váy, áo kiểu", "đầm"));
        assertFalse(FabricText.matchesGarment("Quần jean", "quần tây"));
        assertFalse(FabricText.matchesGarment("Quần jean", "quan ta"));
    }

    @Test
    void mandatoryPropertiesMustBeProvenTogetherOnTheSameFabric() {
        var evidence = new FabricEvidenceService(new FabricRecommendationService(mock(FabricService.class)));
        var rows = List.<Map<String, Object>>of(
                Map.of("fabric_id", "A", "properties_and_cautions", "Mềm"),
                Map.of("fabric_id", "B", "properties_and_cautions", "Không co giãn"),
                Map.of("fabric_id", "C", "properties_and_cautions", "Co giãn"));
        var result = evidence.assessRequired(rows, List.of("mềm", "không co giãn"));
        assertTrue(result.eligible().isEmpty());
        assertEquals(List.of("A", "B"), result.uncertain().stream().map(r -> r.get("fabric_id")).toList());
    }

    @Test
    void unsupportedMandatoryPropertyCannotBePassedByAnEvaluator() {
        AiModelClient approving = new AiModelClient() {
            public String generateJson(String prompt, String input) { fail("Evaluator must not bypass code rules"); return ""; }
            public String getModelName() { return "approving"; }
        };
        var source = Map.<String, Object>of("fabric_id", "A", "currency", "VND",
                "recommended_garments", "Sơ mi", "price_min_vnd_per_m", 1,
                "price_max_vnd_per_m", 2, "properties_and_cautions", "Dệt thoi");
        var result = new FabricValidationService(new ObjectMapper(), approving).validate("Sơ mi",
                criteria(List.of(), List.of("chống cháy")), List.of(source),
                new FabricExplanationService.Draft("Theo nguồn", Map.of("A", "Theo nguồn")));
        assertFalse(result.rulesPassed());
        assertFalse(result.validated());
    }

    @Test
    void oldSevenFieldCriteriaStillWorks() {
        var old = new FabricCriteria("recommend_fabric", "sơ mi", BigDecimal.TEN,
                true, List.of("mềm"), false, null);
        assertEquals(List.of(), old.requiredProperties());
        assertEquals(List.of("mềm"), old.allProperties());
    }

    @Test
    void unknownNegativePropertyCannotDisappearFromIntent() {
        assertTrue(FabricConstraintService.guard("Sơ mi không chứa polyester",
                criteria(List.of(), List.of())).needsClarification());
    }

    @Test
    void neutralUnknownPropertyIsRemovedFromMandatoryList() {
        var result = FabricConstraintService.guard("Sơ mi không cần chống cháy",
                criteria(List.of(), List.of("chống cháy")));
        assertTrue(result.requiredProperties().isEmpty());
        assertFalse(result.needsClarification());
    }

    @Test
    void explicitNegativeUnknownPropertyCannotBeReversedByModel() {
        assertTrue(FabricConstraintService.guard("Sơ mi không chống cháy",
                criteria(List.of("chống cháy"), List.of())).needsClarification());
    }

    @Test
    void mandatoryBudgetIsNotMistakenForMissingMandatoryFabricProperty() {
        assertFalse(FabricConstraintService.guard("Sơ mi bắt buộc dưới 200000 đồng/m",
                criteria(List.of(), List.of())).needsClarification());
    }

    @Test
    void neutralClauseCannotHideAnotherUnknownMandatoryCondition() {
        assertTrue(FabricConstraintService.guard("Bắt buộc chống cháy nhưng không cần co giãn",
                criteria(List.of(), List.of())).needsClarification());
    }
}
