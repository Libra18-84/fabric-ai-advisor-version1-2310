
package com.fabric.fabricaiadvisor;

import com.fabric.fabricaiadvisor.service.FabricRecommendationService;
import com.fabric.fabricaiadvisor.service.FabricService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FabricRankingScoreTest {

    @Test
    void testRankingScoreAndOrder() {

        FabricService fabricService =
                mock(FabricService.class);

        Map<String, Object> fabricA = Map.of(
                "fabric_id", "TEST_A",
                "properties_and_cautions",
                "Thoáng khí, ít nhăn"
        );

        Map<String, Object> fabricB = Map.of(
                "fabric_id", "TEST_B",
                "properties_and_cautions",
                "Thoáng khí"
        );

        Map<String, Object> fabricC = Map.of(
                "fabric_id", "TEST_C",
                "properties_and_cautions",
                "Thoáng; dễ nhăn"
        );

        when(fabricService.getFabricsByGarmentAndBudget(
                "sơ mi", 200000
        )).thenReturn(
                List.of(fabricC, fabricB, fabricA)
        );

        FabricRecommendationService service =
                new FabricRecommendationService(fabricService);

        List<Map<String, Object>> results =
                service.recommend(
                        "sơ mi",
                        200000,
                        "thoáng mát, ít nhăn"
                );

        assertEquals(3, results.size());

        // Vải A: 2 hỗ trợ, 0 cảnh báo
        assertEquals("TEST_A", results.get(0).get("fabric_id"));
        assertEquals(2, results.get(0).get("ranking_score"));

        // Vải B: 1 hỗ trợ, 0 cảnh báo
        assertEquals("TEST_B", results.get(1).get("fabric_id"));
        assertEquals(1, results.get(1).get("ranking_score"));

        // Vải C: 1 hỗ trợ, 1 cảnh báo
        assertEquals("TEST_C", results.get(2).get("fabric_id"));
        assertEquals(0, results.get(2).get("ranking_score"));

        String reason = (String)
                results.get(2).get("recommendation_reason");

        assertNotNull(reason);

        assertTrue(reason.contains("thoáng mát"));
        assertTrue(reason.contains("ít nhăn"));
        assertTrue(reason.contains("1 - 1 = 0"));


        // Điểm cũ vẫn được giữ nguyên
        assertEquals(1, results.get(2).get("match_score"));

        // Có lời giải thích cách tính điểm
        assertNotNull(
                results.get(2).get("ranking_explanation")
        );
    }

    @Test
    void testNegativeRankingScore() {

        FabricService fabricService =
                mock(FabricService.class);

        when(fabricService.getFabricsByGarmentAndBudget(
                "sơ mi", 200000
        )).thenReturn(
                List.of(Map.of(
                        "fabric_id", "TEST_NEGATIVE",
                        "properties_and_cautions",
                        "Dễ nhăn"
                ))
        );

        FabricRecommendationService service =
                new FabricRecommendationService(fabricService);

        List<Map<String, Object>> results =
                service.recommend(
                        "sơ mi",
                        200000,
                        "ít nhăn"
                );

        assertEquals(-1, results.get(0).get("ranking_score"));
        assertEquals(0, results.get(0).get("match_score"));

        String reason = (String)
                results.get(0).get("recommendation_reason");

        assertNotNull(reason);
        assertTrue(reason.contains("ít nhăn"));
        assertTrue(reason.contains("0 - 1 = -1"));

    }
}
