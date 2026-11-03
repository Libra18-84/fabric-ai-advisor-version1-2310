
package com.fabric.fabricaiadvisor;

import com.fabric.fabricaiadvisor.service.FabricService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

//@Component
public class FabricDataTestRunner implements CommandLineRunner {

    private final FabricService fabricService;

    public FabricDataTestRunner(FabricService fabricService) {
        this.fabricService = fabricService;
    }

    @Override
    public void run(String... args) {

        System.out.println("===== KIEM TRA FABRIC SERVICE =====");

        List<Map<String, Object>> fabrics =
                fabricService.getAllFabrics();

        System.out.println("Tong so mau vai: " + fabrics.size());

        for (Map<String, Object> fabric : fabrics) {
            System.out.println(
                    fabric.get("fabric_id") + " - " +
                            fabric.get("fabric_name")
            );
        }
    }
}
