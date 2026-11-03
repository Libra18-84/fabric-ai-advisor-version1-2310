
package com.fabric.fabricaiadvisor.controller;

import com.fabric.fabricaiadvisor.service.FabricRecommendationService;
import com.fabric.fabricaiadvisor.service.FabricRequirementService;
import com.fabric.fabricaiadvisor.service.FabricPreferenceNormalizer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recommendations")
public class FabricRecommendationController {

    private final FabricRecommendationService recommendationService;
    private final FabricRequirementService requirementService;
    private final FabricPreferenceNormalizer preferenceNormalizer;


    public FabricRecommendationController(
            FabricRecommendationService recommendationService,
            FabricRequirementService requirementService,
            FabricPreferenceNormalizer preferenceNormalizer) {

        this.recommendationService = recommendationService;
        this.requirementService = requirementService;
        this.preferenceNormalizer = preferenceNormalizer;
    }


    @GetMapping
    public ResponseEntity<Map<String, Object>> recommend(
            @RequestParam String garment,
            @RequestParam double budget,
            @RequestParam(defaultValue = "") String preference,
            @RequestParam(defaultValue = "") String requirements) {


        if (garment.isBlank()
                || !Double.isFinite(budget)
                || budget < 0) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            "Loai trang phuc hoac ngan sach khong hop le"
                    )
            );
        }


// Kiem tra do dai yeu cau bo sung.
        if (requirements != null && requirements.length() > 1000) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            "Yeu cau bo sung khong duoc vuot qua 1000 ky tu"
                    )
            );
        }

// Trich xuat so thich tu yeu cau bo sung.
        List<String> extractedPreferences =
                requirementService.extractPreferences(requirements);

        List<String> allPreferences = new ArrayList<>();

// Lay so thich nguoi dung nhap truc tiep.
        if (preference != null && !preference.isBlank()) {
            for (String item : preference.split("[,;]+")) {
                String clean = item.trim();

                if (!clean.isEmpty()) {
                    allPreferences.add(clean);
                }
            }
        }

// Them so thich tu yeu cau bo sung.
        allPreferences.addAll(extractedPreferences);

// Chuan hoa va loai bo trung nghia.
        List<String> normalizedPreferences =
                preferenceNormalizer.normalizePreferences(
                        allPreferences
                );

// Tao chuoi dung de cham diem.
        String combinedPreference =
                String.join(", ", normalizedPreferences);



        List<Map<String, Object>> fabrics =
                recommendationService.recommend(
                        garment,
                        budget,
                        combinedPreference
                );

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put("garment", garment);
        response.put("budget", budget);
        response.put("preference", preference);
        response.put("requirements", requirements);

        response.put(
                "extracted_preferences",
                new ArrayList<>(extractedPreferences)
        );

        response.put(
                "combined_preference",
                combinedPreference
        );

        response.put("total", fabrics.size());
        response.put("fabrics", fabrics);
        response.put("ai_connected", false);

        response.put(
                "notice",
                "Ket qua tu van so bo bang quy tac tu khoa. " +
                        "Chi mot so yeu cau bo sung duoc nhan dien. " +
                        "Du lieu vai chua duoc xac minh doc lap."
        );

        return ResponseEntity.ok(response);
    }
}
