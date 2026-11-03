
package com.fabric.fabricaiadvisor;

import com.fabric.fabricaiadvisor.service.FabricRecommendationService;
import com.fabric.fabricaiadvisor.service.FabricService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FabricNegationTest {

    private Map<String, Object> analyze(
            String description,
            String preference) {

        FabricService fabricService =
                mock(FabricService.class);

        when(fabricService.getFabricsByGarmentAndBudget(
                "sơ mi", 200000
        )).thenReturn(
                List.of(Map.of(
                        "fabric_id", "TEST001",
                        "properties_and_cautions",
                        description
                ))
        );

        FabricRecommendationService service =
                new FabricRecommendationService(
                        fabricService
                );

        List<Map<String, Object>> results =
                service.recommend(
                        "sơ mi",
                        200000,
                        preference
                );

        assertEquals(1, results.size());

        return results.get(0);
    }

    @Test
    void testDeNhanLaCanhBao() {

        Map<String, Object> result = analyze(
                "Vải dễ nhăn",
                "ít nhăn"
        );

        assertEquals(-1, result.get("ranking_score"));

        assertEquals(
                List.of("ít nhăn"),
                result.get("caution_preferences")
        );
    }

    @Test
    void testKhongDeNhanKhongPhaiCanhBao() {

        Map<String, Object> result = analyze(
                "Vải không dễ nhăn",
                "ít nhăn"
        );

        assertEquals(
                List.of(),
                result.get("caution_preferences")
        );

        assertTrue(
                (Integer) result.get("ranking_score") >= 0
        );
    }

    @Test
    void testThoangNhungDeNhan() {

        Map<String, Object> result = analyze(
                "Vải thoáng khí nhưng dễ nhăn",
                "thoáng mát, ít nhăn"
        );

        assertEquals(0, result.get("ranking_score"));

        assertEquals(
                List.of("thoáng mát"),
                result.get("matched_preferences")
        );

        assertEquals(
                List.of("ít nhăn"),
                result.get("caution_preferences")
        );
    }

    @Test
    void testKhongMemLaCanhBao() {

        Map<String, Object> result = analyze(
                "Bề mặt vải không mềm",
                "mềm"
        );

        assertEquals(-1, result.get("ranking_score"));

        assertEquals(
                List.of("mềm"),
                result.get("caution_preferences")
        );
    }
}
