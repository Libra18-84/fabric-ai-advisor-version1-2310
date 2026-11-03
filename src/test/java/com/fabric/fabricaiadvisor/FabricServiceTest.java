
package com.fabric.fabricaiadvisor;

import com.fabric.fabricaiadvisor.service.FabricService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FabricServiceTest {

    @Autowired
    private FabricService fabricService;

    @Test
    void testFilterByBudget() {

        List<Map<String, Object>> fabrics =
                fabricService.getFabricsByBudget(100000);

        assertEquals(11, fabrics.size());

        for (Map<String, Object> fabric : fabrics) {
            Number price = (Number)
                    fabric.get("price_min_vnd_per_m");

            assertNotNull(price);
            assertTrue(price.doubleValue() <= 100000);
        }
    }

    @Test
    void testNegativeBudget() {

        assertThrows(
                IllegalArgumentException.class,
                () -> fabricService.getFabricsByBudget(-1000)
        );
    }

    @Test
    void testFilterByGarment() {

        List<Map<String, Object>> fabrics =
                fabricService.getFabricsByGarment("sơ mi");

        assertNotNull(fabrics);

        // Kiem tra danh sach khong rong
        assertFalse(
                fabrics.isEmpty(),
                "Khong tim thay mau vai nao cho ao so mi"
        );

        // Kiem tra tung mau vai co chua tu khoa so mi
        for (Map<String, Object> fabric : fabrics) {
            String garments = (String)
                    fabric.get("recommended_garments");

            assertNotNull(garments);

            assertTrue(
                    garments.toLowerCase().contains("sơ mi")
            );
        }
    }

    @Test
    void testEmptyGarment() {

        assertThrows(
                IllegalArgumentException.class,
                () -> fabricService.getFabricsByGarment("")
        );
    }

    @Test
    void testFilterByGarmentAndBudget() {

        List<Map<String, Object>> fabrics =
                fabricService.getFabricsByGarmentAndBudget(
                        "sơ mi", 200000
                );

        assertNotNull(fabrics);
        assertFalse(
                fabrics.isEmpty(),
                "Khong tim thay vai cho so mi trong ngan sach"
        );

        for (Map<String, Object> fabric : fabrics) {

            String garments = (String)
                    fabric.get("recommended_garments");

            Number price = (Number)
                    fabric.get("price_min_vnd_per_m");

            assertNotNull(garments);
            assertNotNull(price);

            assertTrue(
                    garments.toLowerCase().contains("sơ mi")
            );

            assertTrue(
                    price.doubleValue() <= 200000
            );
        }
    }

    @Test
    void testCombinedFilterInvalidInputs() {

        assertThrows(
                IllegalArgumentException.class,
                () -> fabricService.getFabricsByGarmentAndBudget(
                        "", 200000
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> fabricService.getFabricsByGarmentAndBudget(
                        "sơ mi", -1000
                )
        );
    }

}
