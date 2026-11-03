
package com.fabric.fabricaiadvisor.controller;

import com.fabric.fabricaiadvisor.service.FabricService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fabrics")
public class FabricSearchController {

    private final FabricService fabricService;

    public FabricSearchController(FabricService fabricService) {
        this.fabricService = fabricService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllFabrics() {

        List<Map<String, Object>> fabrics =
                fabricService.getAllFabrics();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", fabrics.size());
        response.put("fabrics", fabrics);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchFabrics(
            @RequestParam String garment,
            @RequestParam double budget) {

        if (garment.isBlank() ||
                !Double.isFinite(budget) ||
                budget < 0) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error",
                            "Loai trang phuc hoac ngan sach khong hop le"
                    ));
        }

        List<Map<String, Object>> fabrics =
                fabricService.getFabricsByGarmentAndBudget(
                        garment, budget
                );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("garment", garment);
        response.put("budget", budget);
        response.put("total", fabrics.size());
        response.put("fabrics", fabrics);

        return ResponseEntity.ok(response);
    }
}
