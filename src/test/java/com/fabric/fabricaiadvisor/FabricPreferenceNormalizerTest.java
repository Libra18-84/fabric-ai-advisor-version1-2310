
package com.fabric.fabricaiadvisor;

import com.fabric.fabricaiadvisor.service.FabricPreferenceNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FabricPreferenceNormalizerTest {

    private final FabricPreferenceNormalizer service =
            new FabricPreferenceNormalizer();

    @Test
    void testNormalizeSoftness() {
        assertEquals(
                "mềm",
                service.normalizePreference("Mềm mại")
        );
    }

    @Test
    void testNormalizeBreathability() {
        assertEquals(
                "thoáng mát",
                service.normalizePreference("Thoáng khí")
        );
    }

    @Test
    void testNormalizeWrinkleResistance() {
        assertEquals(
                "ít nhăn",
                service.normalizePreference("Chống nhăn")
        );
    }

    @Test
    void testRemoveDuplicatePreferences() {
        List<String> input = List.of(
                "mềm mại",
                "mềm",
                "thoáng khí",
                "thoáng mát"
        );

        List<String> result =
                service.normalizePreferences(input);

        assertEquals(
                List.of("mềm", "thoáng mát"),
                result
        );
    }

    @Test
    void testEmptyPreferenceList() {
        assertTrue(
                service.normalizePreferences(List.of())
                        .isEmpty()
        );
    }

    @Test
    void testUnknownPreferenceDoesNotDisappear() {
        assertEquals(
                "de giat",
                service.normalizePreference("Dễ giặt")
        );
    }
}
