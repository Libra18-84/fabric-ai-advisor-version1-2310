package com.fabric.fabricaiadvisor;

import com.fabric.fabricaiadvisor.dto.FabricCriteria;
import com.fabric.fabricaiadvisor.repository.FabricRepository;
import com.fabric.fabricaiadvisor.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FabricEvidenceServiceTest {

    private FabricRecommendationService ranking;
    private FabricEvidenceService evidence;
    private FabricIntentService intent;
    private FabricRepository repository;
    private FabricExplanationService explanation;
    private FabricValidationService validation;
    private FabricAdviceService advice;

    @BeforeEach
    void setUp() {
        ranking = new FabricRecommendationService(
                mock(FabricService.class)
        );
        evidence = new FabricEvidenceService(ranking);

        intent = mock(FabricIntentService.class);
        repository = mock(FabricRepository.class);
        explanation = mock(FabricExplanationService.class);
        validation = mock(FabricValidationService.class);

        when(validation.notRun(anyString())).thenReturn(
                new FabricValidationService.Result(
                        false, "not_run", List.of(), "test"
                )
        );

        advice = new FabricAdviceService(
                intent, repository, ranking,
                explanation, validation, evidence
        );
    }

    private Map<String, Object> fabric(
            String id, String description) {

        return Map.of(
                "fabric_id", id,
                "fabric_name", "Vải " + id,
                "recommended_garments", "Áo sơ mi",
                "properties_and_cautions", description,
                "price_min_vnd_per_m", 45000,
                "price_max_vnd_per_m", 90000,
                "currency", "VND",
                "verification_status", "unverified"
        );
    }

    private FabricCriteria criteria() {
        return new FabricCriteria(
                "recommend_fabric",
                "sơ mi",
                new BigDecimal("200000"),
                true,
                List.of("mềm"),
                false,
                null
        );
    }

    @Test
    void reportsAttributeUnknownForEveryCandidate() {
        var ranked = ranking.rank(
                List.of(
                        fabric("F1", "Thoáng khí"),
                        fabric("F2", "Ít nhăn")
                ),
                List.of("mềm")
        );

        assertEquals(
                List.of("mềm"),
                evidence.unknownForAll(ranked)
        );
    }

    @Test
    void oneCandidateWithEvidencePreventsGlobalMissingConclusion() {
        var ranked = ranking.rank(
                List.of(
                        fabric("F1", "Mềm mại"),
                        fabric("F2", "Ít nhăn")
                ),
                List.of("mềm")
        );

        assertTrue(evidence.unknownForAll(ranked).isEmpty());
    }

    @Test
    void unsupportedKeywordIsNotAssumedAbsentFromSource() {
        var ranked = ranking.rank(
                List.of(fabric("F1", "Mỏng, nhẹ")),
                List.of("mỏng")
        );

        assertTrue(evidence.unknownForAll(ranked).isEmpty());
    }

    @Test
    void contraryEvidenceIsDifferentFromUnknownEvidence() {
        var ranked = ranking.rank(
                List.of(
                        fabric("F1", "Không mềm"),
                        fabric("F2", "Ít nhăn")
                ),
                List.of("mềm")
        );

        assertTrue(evidence.unknownForAll(ranked).isEmpty());
    }

    @Test
    void noCandidatesDoesNotMeanMissingAttribute() {
        assertTrue(evidence.unknownForAll(List.of()).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingEvidenceReturnsCandidatesWithoutCallingAI() {
        String query = "Sơ mi mềm không quá 200000 đồng/m";
        var criteria = criteria();

        when(intent.extract(query)).thenReturn(criteria);

        when(repository.findCandidates(criteria)).thenReturn(
                List.of(
                        fabric("F1", "Thoáng khí"),
                        fabric("F2", "Ít nhăn")
                )
        );

        var result = advice.advise(query);

        assertEquals(
                "insufficient_data",
                result.get("status")
        );

        assertEquals(
                List.of("mềm"),
                result.get("missingPreferences")
        );

        assertTrue(
                ((List<?>) result.get("recommendations")).isEmpty()
        );

        var candidates = (List<Map<String, Object>>)
                result.get("candidates");

        assertEquals(2, candidates.size());

        assertEquals(
                "unverified",
                ((Map<?, ?>) candidates.getFirst().get("fabric"))
                        .get("verification_status")
        );

        assertEquals(
                false,
                ((Map<?, ?>) result.get("factCheck")).get("validated")
        );

        assertEquals(0, result.get("explanationAttempts"));

        verifyNoInteractions(explanation);

        verify(validation, never()).validate(
                anyString(), any(), anyList(), any()
        );
    }

    @Test
    void emptyQueryResultStillReturnsNoResults() {
        String query = "Sơ mi dưới 1 đồng/m";

        var criteria = new FabricCriteria(
                "recommend_fabric",
                "sơ mi",
                BigDecimal.ONE,
                false,
                List.of(),
                false,
                null
        );

        when(intent.extract(query)).thenReturn(criteria);

        when(repository.findCandidates(criteria))
                .thenReturn(List.of());

        assertEquals(
                "no_results",
                advice.advise(query).get("status")
        );

        verifyNoInteractions(explanation);
    }

    @Test
    void provenAttributeContinuesToGenerationAndValidation() {
        String query = "Sơ mi mềm không quá 200000 đồng/m";
        var criteria = criteria();

        when(intent.extract(query)).thenReturn(criteria);

        when(repository.findCandidates(criteria)).thenReturn(
                List.of(fabric("F1", "Mềm mại"))
        );

        var draft = new FabricExplanationService.Draft(
                "Mô tả theo nguồn",
                Map.of("F1", "Mềm theo nguồn")
        );

        when(explanation.generate(
                eq(query), eq(criteria), anyList()
        )).thenReturn(draft);

        when(validation.validate(
                eq(query), eq(criteria), anyList(), any(FabricExplanationService.Draft.class)
        )).thenReturn(
                new FabricValidationService.Result(
                        true, "passed", List.of(), "test"
                )
        );

        assertEquals(
                "success",
                advice.advise(query).get("status")
        );

        verify(explanation).generate(
                eq(query), eq(criteria), anyList()
        );

        var captured = org.mockito.ArgumentCaptor.forClass(FabricExplanationService.Draft.class);
        verify(validation).validate(eq(query), eq(criteria), anyList(), captured.capture());
        assertEquals(draft.reasonsById(), captured.getValue().reasonsById());
        assertTrue(captured.getValue().explanation().contains("theo catalog"));
    }
}