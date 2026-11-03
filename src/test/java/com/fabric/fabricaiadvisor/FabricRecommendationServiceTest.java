
package com.fabric.fabricaiadvisor;

import com.fabric.fabricaiadvisor.service.FabricRecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FabricRecommendationServiceTest {

    @Autowired
    private FabricRecommendationService recommendationService;

    @Test
    void testRecommendForShirt() {

        List<Map<String, Object>> results =
                recommendationService.recommend(
                        "sơ mi",
                        200000,
                        "thoáng khí, ít nhăn"
                );

        assertNotNull(results);
        assertEquals(3, results.size());

        for (Map<String, Object> fabric : results) {
            assertNotNull(fabric.get("fabric_id"));
            assertNotNull(fabric.get("match_score"));
            assertNotNull(fabric.get("matched_preferences"));
        }
    }

    @Test
    void testResultsSortedByScore() {

        List<Map<String, Object>> results =
                recommendationService.recommend(
                        "sơ mi",
                        200000,
                        "thoáng khí, ít nhăn"
                );

        for (int i = 1; i < results.size(); i++) {

            int previousScore = (Integer)
                    results.get(i - 1).get("match_score");

            int currentScore = (Integer)
                    results.get(i).get("match_score");

            assertTrue(previousScore >= currentScore);
        }
    }

    @Test
    void testInvalidBudget() {

        assertThrows(
                IllegalArgumentException.class,
                () -> recommendationService.recommend(
                        "sơ mi", -1000, "mềm"
                )
        );
    }

    @Test
    void testEmptyPreference() {

        List<Map<String, Object>> results =
                recommendationService.recommend(
                        "sơ mi", 200000, ""
                );

        assertEquals(3, results.size());

        for (Map<String, Object> fabric : results) {
            assertEquals(0, fabric.get("match_score"));
        }
    }

    @Test
    void testPreferenceAnalysis() {

        List<Map<String, Object>> results =
                recommendationService.recommend(
                        "sơ mi",
                        200000,
                        "thoáng mát, ít nhăn, mềm"
                );

        assertEquals(3, results.size());

        for (Map<String, Object> fabric : results) {

            assertNotNull(fabric.get("match_score"));

            assertNotNull(
                    fabric.get("matched_preferences")
            );

            assertNotNull(
                    fabric.get("caution_preferences")
            );

            assertNotNull(
                    fabric.get("unknown_preferences")
            );

            int score = (Integer)
                    fabric.get("match_score");

            assertTrue(score >= 0);
            assertTrue(score <= 3);
        }
    }

    @Test
    void testPreferenceClassification() {

        List<Map<String, Object>> results =
                recommendationService.recommend(
                        "sơ mi",
                        200000,
                        "thoáng mát, ít nhăn"
                );

        assertFalse(results.isEmpty());

        for (Map<String, Object> fabric : results) {

            @SuppressWarnings("unchecked")
            List<String> matched = (List<String>)
                    fabric.get("matched_preferences");

            @SuppressWarnings("unchecked")
            List<String> cautions = (List<String>)
                    fabric.get("caution_preferences");

            @SuppressWarnings("unchecked")
            List<String> unknown = (List<String>)
                    fabric.get("unknown_preferences");

            assertNotNull(matched);
            assertNotNull(cautions);
            assertNotNull(unknown);

            for (String preference : matched) {
                assertFalse(cautions.contains(preference));
                assertFalse(unknown.contains(preference));
            }

            int score = (Integer)
                    fabric.get("match_score");

            assertEquals(matched.size(), score);
        }
    }

}
