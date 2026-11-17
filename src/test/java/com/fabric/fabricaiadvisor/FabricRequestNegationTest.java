package com.fabric.fabricaiadvisor;

import com.fabric.fabricaiadvisor.service.FabricPreferenceNormalizer;
import com.fabric.fabricaiadvisor.service.FabricRecommendationService;
import com.fabric.fabricaiadvisor.service.FabricRequirementService;
import com.fabric.fabricaiadvisor.service.FabricService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FabricRequestNegationTest {

    private final FabricRequirementService requirements =
            new FabricRequirementService();

    private final FabricPreferenceNormalizer normalizer =
            new FabricPreferenceNormalizer();

    private final FabricRecommendationService ranking =
            new FabricRecommendationService(
                    mock(FabricService.class)
            );

    private Map<String, Object> analyze(
            String description,
            String preference) {

        return ranking.rank(
                List.of(
                        Map.of(
                                "fabric_id", "TEST",
                                "properties_and_cautions", description
                        )
                ),
                List.of(preference)
        ).getFirst();
    }

    @Test
    void noWantSoftnessIsNegative() {
        assertEquals(
                List.of("không mềm"),
                requirements.extractPreferences(
                        "Tôi không muốn vải mềm"
                )
        );
    }

    @Test
    void noNeedStretchIsNeutral() {
        assertEquals(
                List.of(),
                requirements.extractPreferences(
                        "Tôi không cần vải co giãn"
                )
        );
    }

    @Test
    void neutralDoesNotCreateRankingPoints() {
        assertEquals(
                0,
                analyze(
                        "Co giãn",
                        "không cần co giãn"
                ).get("ranking_score")
        );
    }

    @Test
    void noStretchRequestMatchesExplicitAbsence() {
        assertEquals(
                1,
                analyze(
                        "Không co giãn",
                        "không co giãn"
                ).get("ranking_score")
        );
    }

    @Test
    void noStretchRequestWarnsAboutStretch() {
        assertEquals(
                -1,
                analyze(
                        "Co giãn",
                        "không co giãn"
                ).get("ranking_score")
        );
    }

    @Test
    void littleStretchDoesNotProveNoStretch() {
        assertEquals(
                List.of("không co giãn"),
                analyze(
                        "Ít co giãn",
                        "không co giãn"
                ).get("unknown_preferences")
        );
    }

    @Test
    void absentInformationDoesNotProveNoStretch() {
        assertEquals(
                List.of("không co giãn"),
                analyze(
                        "Dệt thoi",
                        "không co giãn"
                ).get("unknown_preferences")
        );
    }

    @Test
    void negativeSynonymsKeepTheirDirection() {
        assertEquals(
                "không co giãn",
                normalizer.normalizePreference(
                        "KHÔNG ĐÀN HỒI"
                )
        );

        assertEquals(
                "",
                normalizer.normalizePreference(
                        "không cần co giãn"
                )
        );
    }

    @Test
    void neutralClauseDoesNotSwallowSoftness() {
        assertEquals(
                List.of("mềm"),
                requirements.extractPreferences(
                        "Không cần co giãn nhưng muốn vải mềm"
                )
        );
    }
}