
package com.fabric.fabricaiadvisor;

import com.fabric.fabricaiadvisor.service.FabricRequirementService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FabricRequirementServiceTest {

    private final FabricRequirementService service =
            new FabricRequirementService();

    @Test
    void testExtractMultiplePreferences() {

        List<String> result =
                service.extractPreferences(
                        "Tôi muốn vải thoáng mát, " +
                                "ít nhăn và mềm mại"
                );

        assertEquals(
                List.of("thoáng mát", "ít nhăn", "mềm"),
                result
        );
    }

    @Test
    void testExtractStretchAndAbsorption() {

        List<String> result =
                service.extractPreferences(
                        "Cần co giãn và thấm hút tốt"
                );

        assertEquals(
                List.of("co giãn", "thấm hút"),
                result
        );
    }

    @Test
    void testEmptyRequirements() {

        assertTrue(
                service.extractPreferences("").isEmpty()
        );
    }

    @Test
    void testUnknownRequirements() {

        assertTrue(
                service.extractPreferences(
                        "Tôi muốn may áo đi làm"
                ).isEmpty()
        );
    }

    @Test
    void testKhongCanCoGian() {

        List<String> result =
                service.extractPreferences(
                        "Tôi không cần vải co giãn"
                );

        assertFalse(result.contains("co giãn"));
    }

    @Test
    void testKhongCanItNhan() {

        List<String> result =
                service.extractPreferences(
                        "Tôi không cần vải ít nhăn"
                );

        assertFalse(result.contains("ít nhăn"));
    }

    @Test
    void testCoGianDuocYeuCauRoRang() {

        List<String> result =
                service.extractPreferences(
                        "Tôi cần vải co giãn"
                );

        assertTrue(result.contains("co giãn"));
    }

    @Test
    void testKetHopYeuCauVaPhuDinh() {

        List<String> result =
                service.extractPreferences(
                        "Tôi cần vải mềm mại, thoáng mát " +
                                "nhưng không cần co giãn"
                );

        assertTrue(result.contains("mềm"));
        assertTrue(result.contains("thoáng mát"));
        assertFalse(result.contains("co giãn"));
    }

}
