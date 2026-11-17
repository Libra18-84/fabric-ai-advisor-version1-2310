package com.fabric.fabricaiadvisor.service;

import com.fabric.fabricaiadvisor.dto.FabricCriteria;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FabricBudgetRulesTest {
    private FabricCriteria model(BigDecimal amount, boolean inclusive) {
        return new FabricCriteria("recommend_fabric", "sơ mi", amount, inclusive,
                List.of("thoáng mát"), false, null, List.of());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "dưới 90.000 đồng/m|90000|false",
            "tối đa 90.000 đồng/m|90000|true",
            "không quá 200.000 đồng/m|200000|true",
            "ít hơn 90,000 VND/mét|90000|false",
            "nhỏ hơn 90 000 đ/m|90000|false",
            "không vượt quá 200k/m|200000|true",
            "tối đa 0,2 triệu đồng/m|200000|true",
            "tối đa 1.5 tr/m|1500000|true",
            "tối đa 1 đồng/m|1|true",
            "dưới 1 đồng/m|1|false",
            "tối đa 0 đồng/m|0|true",
            "không quá 200 nghìn đồng mỗi mét|200000|true",
            "ngân sách 200 ngàn/mét|200000|true",
            "dưới 90.000đ/m|90000|false"
    })
    void explicitTextOverridesModelAmountAndBoundary(String price, String amount, boolean inclusive) {
        var result = FabricBudgetRules.guard("Tôi may sơ mi, " + price, Map.of(), model(new BigDecimal("777"), !inclusive));
        assertFalse(result.needsClarification(), price);
        assertEquals(0, new BigDecimal(amount).compareTo(result.maxPriceVndPerMeter()), price);
        assertEquals(inclusive, result.budgetInclusive());
        assertEquals(List.of("thoáng mát"), result.preferences());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Tổng ngân sách mua vải là 200.000 đồng.", "Tổng chi phí 200000 đồng, tối đa 90000 đồng/m.",
            "tối đa 10 USD/m", "tối đa $10/m", "tối đa 10 EUR/m", "tối đa 200000 đồng/kg",
            "tối đa 200000 đồng/m2", "tối đa 200000 đồng/mét vuông", "tối đa -1 đồng/m",
            "tối thiểu 90000 đồng/m", "trên 90000 đồng/m", "từ 50000 đến 90000 đồng/m",
            "tối đa 90000 đồng/m và tối đa 80000 đồng/m", "không cần dưới 90000 đồng/m",
            "tối đa 1e5 đồng/m", "tối đa 90.00 đồng/m", "Tôi cần vải may sơ mi.",
            "tối đa âm 100000 đồng/m", "tối đa −100000 đồng/m"
    })
    void unsupportedOrAmbiguousPriceCannotInheritAnInventedModelBudget(String query) {
        var result = FabricBudgetRules.guard(query, Map.of(), model(new BigDecimal("200000"), true));
        assertTrue(result.needsClarification(), query);
        assertNull(result.maxPriceVndPerMeter());
        assertNull(result.budgetInclusive());
        assertFalse(result.clarificationQuestion().isBlank());
    }

    @Test
    void contextCanSupplyMissingPriceButCannotReplaceAnExplicitQueryPrice() {
        var context = Map.<String, Object>of("context", "Ngân sách tối đa 150000 đồng/m");
        var fromContext = FabricBudgetRules.guard("Tôi cần vải may áo sơ mi.", context, model(BigDecimal.TEN, false));
        assertEquals(new BigDecimal("150000"), fromContext.maxPriceVndPerMeter());
        assertEquals(new BigDecimal("150000"), FabricBudgetRules.guard("Tôi muốn tư vấn giá vải may sơ mi.",
                context, model(BigDecimal.TEN, false)).maxPriceVndPerMeter());
        var fromQuery = FabricBudgetRules.guard("Sơ mi dưới 90000 đồng/m", context, model(BigDecimal.TEN, true));
        assertEquals(new BigDecimal("90000"), fromQuery.maxPriceVndPerMeter());
        assertFalse(fromQuery.budgetInclusive());
        assertTrue(FabricBudgetRules.guard("Tổng ngân sách mua vải là 200000 đồng", context,
                model(BigDecimal.TEN, true)).needsClarification());
    }

    @Test
    void intentServiceUsesTheGuardForTheThreeObservedModelErrors() {
        var mapper = new ObjectMapper();
        AiModelClient stub = new AiModelClient() {
            public String getModelName() { return "wrong-price-model"; }
            public String generateJson(String prompt, String input) {
                return mapper.writeValueAsString(model(new BigDecimal("100000"), true));
            }
        };
        var service = new FabricIntentService(stub, mapper);
        var strict = service.extract("Sơ mi dưới 90000 đồng/m");
        assertEquals(new BigDecimal("90000"), strict.maxPriceVndPerMeter());
        assertFalse(strict.budgetInclusive());
        assertEquals(BigDecimal.ONE, service.extract("Sơ mi tối đa 1 đồng/m").maxPriceVndPerMeter());
        var total = service.extract("Sơ mi, tổng ngân sách mua vải là 200000 đồng");
        assertTrue(total.needsClarification());
        assertNull(total.maxPriceVndPerMeter());
    }

    @Test
    void nonBreakingThousandsSeparatorsArePreservedAsTheSamePrice() {
        for (char space : new char[]{'\u00a0', '\u202f'}) {
            var result = FabricBudgetRules.guard("Sơ mi dưới 90" + space + "000 đồng/m", Map.of(), model(BigDecimal.ONE, true));
            assertEquals(new BigDecimal("90000"), result.maxPriceVndPerMeter());
        }
    }
}
