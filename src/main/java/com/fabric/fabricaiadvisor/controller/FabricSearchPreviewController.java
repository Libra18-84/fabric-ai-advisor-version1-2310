package com.fabric.fabricaiadvisor.controller;

import com.fabric.fabricaiadvisor.dto.FabricCriteria;
import com.fabric.fabricaiadvisor.repository.FabricRepository;
import com.fabric.fabricaiadvisor.service.FabricEvidenceService;
import com.fabric.fabricaiadvisor.service.FabricRecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/fabrics")
public class FabricSearchPreviewController {
    private final FabricRepository repository;
    private final FabricRecommendationService ranking;
    private final FabricEvidenceService evidence;

    public FabricSearchPreviewController(FabricRepository repository,
                                         FabricRecommendationService ranking,
                                         FabricEvidenceService evidence) {
        this.repository = repository;
        this.ranking = ranking;
        this.evidence = evidence;
    }

    @PostMapping("/preview")
    public ResponseEntity<?> preview(@RequestBody FabricCriteria criteria) {
        var ranked = ranking.rank(repository.findCandidates(criteria), criteria.allProperties());
        var assessment = evidence.assessRequired(ranked, criteria.requiredProperties());
        String status = !assessment.eligible().isEmpty() ? "ok"
                : !assessment.uncertain().isEmpty() ? "insufficient_data" : "no_results";
        return ResponseEntity.ok(Map.of(
                "status", status, "total", assessment.eligible().size(),
                "fabrics", assessment.eligible(),
                "candidates", assessment.eligible().isEmpty() ? assessment.uncertain() : List.of(),
                "missingProperties", assessment.missing()));
    }
}
