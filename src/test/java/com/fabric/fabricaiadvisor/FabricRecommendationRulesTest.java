
package com.fabric.fabricaiadvisor;

import com.fabric.fabricaiadvisor.service.FabricRecommendationService;
import com.fabric.fabricaiadvisor.service.FabricService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FabricRecommendationRulesTest {

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
                        "sơ mi", 200000, preference
                );

        assertEquals(1, results.size());

        return results.get(0);
    }

    @Test
    void testThongThoangVaDeNhan() {

        Map<String, Object> result = analyze(
                "Thoáng; dễ nhăn",
                "thoáng mát, ít nhăn"
        );

        assertEquals(1, result.get("match_score"));

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
    void testKhongMem() {

        Map<String, Object> result = analyze(
                "Không mềm",
                "mềm"
        );

        assertEquals(0, result.get("match_score"));

        assertEquals(
                List.of("mềm"),
                result.get("caution_preferences")
        );
    }

    @Test
    void testKhongCoGian() {

        Map<String, Object> result = analyze(
                "Không co giãn",
                "co giãn"
        );

        assertEquals(0, result.get("match_score"));

        assertEquals(
                List.of("co giãn"),
                result.get("caution_preferences")
        );
    }

    @Test
    void testItNhanHonCotton() {

        Map<String, Object> result = analyze(
                "Ít nhăn hơn cotton",
                "ít nhăn"
        );

        assertEquals(1, result.get("match_score"));

        assertEquals(
                List.of("ít nhăn"),
                result.get("matched_preferences")
        );
    }

    @Test
    void testThieuThongTin() {

        Map<String, Object> result = analyze(
                "Dệt thoi, GSM 140",
                "mềm"
        );

        assertEquals(0, result.get("match_score"));

        assertEquals(
                List.of("mềm"),
                result.get("unknown_preferences")
        );
    }
}
