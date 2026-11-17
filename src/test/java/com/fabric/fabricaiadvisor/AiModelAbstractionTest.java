package com.fabric.fabricaiadvisor;

import com.fabric.fabricaiadvisor.dto.FabricCriteria;
import com.fabric.fabricaiadvisor.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AiModelAbstractionTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static class StubModel implements AiModelClient {

        private final String response;
        private String input;

        StubModel(String response) {
            this.response = response;
        }

        @Override
        public String generateJson(
                String systemPrompt,
                String input) {

            this.input = input;
            return response;
        }

        @Override
        public String getModelName() {
            return "stub-model";
        }
    }

    private FabricCriteria criteria() {
        return new FabricCriteria(
                "recommend_fabric",
                "sơ mi",
                new BigDecimal("200000"),
                true,
                List.of(),
                false,
                null
        );
    }

    private List<Map<String, Object>> sources() {
        return List.of(
                Map.of(
                        "fabric_id", "TEST001",
                        "currency", "VND",
                        "price_min_vnd_per_m", 50000,
                        "price_max_vnd_per_m", 90000,
                        "recommended_garments", "Áo sơ mi",
                        "properties_and_cautions", "Thoáng khí",
                        "verification_status", "unverified"
                )
        );
    }

    private FabricExplanationService.Draft draft() {
        return new FabricExplanationService.Draft(
                "Theo dữ liệu nguồn",
                Map.of("TEST001", "Thoáng theo mô tả")
        );
    }

    @Test
    void intentAcceptsAnotherImplementation() {
        var stub = new StubModel(
                mapper.writeValueAsString(criteria())
        );

        var service = new FabricIntentService(stub, mapper);

        var result = service.extract(
                "Sơ mi không quá 200000 đồng/m"
        );

        assertEquals("sơ mi", result.garment());
        assertFalse(result.needsClarification());

        assertEquals(
                "Sơ mi không quá 200000 đồng/m",
                stub.input
        );
    }

    @Test
    void explanationPassesSqlRecordsThroughInterface() {
        var stub = new StubModel("""
                {
                  "explanation": "Theo nguồn",
                  "recommendations": [
                    {
                      "fabricId": "TEST001",
                      "reason": "Thoáng theo nguồn"
                    }
                  ]
                }
                """);

        var service = new FabricExplanationService(stub, mapper);

        var result = service.generate(
                "Sơ mi",
                criteria(),
                sources()
        );

        assertEquals(
                Set.of("TEST001"),
                result.reasonsById().keySet()
        );

        var payload = mapper.readTree(stub.input);

        assertEquals(
                "TEST001",
                payload.path("fabrics")
                        .get(0)
                        .path("fabric_id")
                        .asText()
        );
    }

    @Test
    void evaluatorUsesInterfaceAndReportsModelName() {
        var stub = new StubModel("""
                {"passed": true, "issues": []}
                """);

        var service = new FabricValidationService(mapper, stub);

        var result = service.validate(
                "Sơ mi",
                criteria(),
                sources(),
                draft()
        );

        assertTrue(result.validated());
        assertEquals("stub-model", result.model());

        assertEquals(
                "TEST001",
                mapper.readTree(stub.input)
                        .path("sources")
                        .get(0)
                        .path("fabric_id")
                        .asText()
        );
    }

    @Test
    void inconsistentEvaluationCannotBeValidated() {
        var stub = new StubModel("""
                {"passed": true, "issues": ["Mâu thuẫn"]}
                """);

        var result = new FabricValidationService(mapper, stub)
                .validate(
                        "Sơ mi",
                        criteria(),
                        sources(),
                        draft()
                );

        assertFalse(result.validated());
        assertEquals(
                "invalid_response",
                result.evaluationStatus()
        );
    }

    @Test
    void missingEvaluatorRemainsUnavailable() {
        var result = new FabricValidationService(
                mapper,
                (AiModelClient) null
        ).validate(
                "Sơ mi",
                criteria(),
                sources(),
                draft()
        );

        assertFalse(result.validated());
        assertEquals(
                "unavailable",
                result.evaluationStatus()
        );
    }

    @Test
    void transportFailureRemainsUnavailable() {
        var stub = new StubModel("") {
            @Override
            public String generateJson(
                    String systemPrompt,
                    String input) {

                throw new ResourceAccessException("offline");
            }
        };

        var result = new FabricValidationService(mapper, stub)
                .validate(
                        "Sơ mi",
                        criteria(),
                        sources(),
                        draft()
                );

        assertFalse(result.validated());
        assertEquals(
                "unavailable",
                result.evaluationStatus()
        );
    }

    @Test
    void invalidProviderResponseIsRejected() {
        var stub = new StubModel("") {
            @Override
            public String generateJson(
                    String systemPrompt,
                    String input) {

                throw new IllegalStateException(
                        "Invalid provider response"
                );
            }
        };

        var result = new FabricValidationService(mapper, stub)
                .validate(
                        "Sơ mi",
                        criteria(),
                        sources(),
                        draft()
                );

        assertFalse(result.validated());
        assertEquals(
                "invalid_response",
                result.evaluationStatus()
        );
    }
}