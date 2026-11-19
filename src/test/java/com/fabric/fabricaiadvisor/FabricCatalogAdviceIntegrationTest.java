package com.fabric.fabricaiadvisor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "fabric.advice.catalog-only=true",
        "spring.datasource.url=jdbc:sqlite:file:./fabric.db?mode=ro",
        "ai.models-file=",
        "ai.evaluation.model="
})
@Import(FabricAdviceFlowIntegrationTest.Config.class)
class FabricCatalogAdviceIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FabricAdviceFlowIntegrationTest.MainStub main;
    @Autowired private FabricAdviceFlowIntegrationTest.EvaluationStub evaluator;

    @BeforeEach
    void reset() {
        main.mode = "normal";
        main.lastIntent = "";
        main.explanations.set(0);
        evaluator.mode = "offline";
        evaluator.calls.set(0);
    }

    @AfterEach
    void noModelCallsBeyondIntent() {
        assertFalse(main.lastIntent.isBlank());
        assertEquals(0, main.explanations.get());
        assertEquals(0, evaluator.calls.get());
    }

    @Test
    void returnsCatalogFactsAndKeepsVerificationStatus() throws Exception {
        var response = ask("Tôi cần áo sơ mi, không quá 200.000 đồng/m.");
        assertEquals("fallback", response.path("status").asText());
        assertEquals("not_run", response.at("/factCheck/evaluationStatus").asText());
        assertTrue(response.at("/factCheck/rulesPassed").asBoolean());
        assertFalse(response.at("/factCheck/validated").asBoolean());
        assertEquals(0, response.path("explanationAttempts").asInt(-1));
        assertEquals(1, response.at("/timings/stages/intent/calls").asInt());
        assertEquals(0, response.at("/timings/stages/explanation/calls").asInt(-1));
        assertEquals(1, response.at("/timings/stages/validation/calls").asInt());
        assertEquals(List.of("FAB001", "FAB005", "FAB006"), ids(response));
        for (JsonNode recommendation : response.path("recommendations")) {
            var fabric = recommendation.path("fabric");
            var source = jdbc.queryForMap("SELECT * FROM fabric_database WHERE fabric_id = ?",
                    fabric.path("fabric_id").asText());
            for (String field : List.of("fiber_composition", "typical_width", "recommended_garments",
                    "properties_and_cautions", "verification_status", "source_url")) {
                assertEquals(source.get(field), fabric.path(field).asText(), field);
            }
            assertEquals(((Number) source.get("price_max_vnd_per_m")).doubleValue(),
                    fabric.path("price_max_vnd_per_m").asDouble());
        }
    }

    @Test
    void distinguishesStrictAndInclusiveBudget() throws Exception {
        assertEquals(List.of("FAB001", "FAB005"), ids(ask("Tôi cần sơ mi, không quá 90.000 đồng/m.")));
        assertEquals(List.of("FAB005"), ids(ask("Tôi cần sơ mi, dưới 90.000 đồng/m.")));
    }

    @Test
    void leavesAnUnsupportedRequiredPropertyUnconfirmed() throws Exception {
        var response = ask("Tôi cần sơ mi bắt buộc mềm, không quá 200.000 đồng/m.");
        assertEquals("insufficient_data", response.path("status").asText());
        assertTrue(ids(response).isEmpty());
        assertEquals("mềm", response.at("/missingPreferences/0").asText());
        assertEquals(3, response.path("candidates").size());
    }

    @Test
    void keepsClarificationAndNoResults() throws Exception {
        assertEquals("needs_clarification", ask("Tôi cần áo sơ mi với tổng ngân sách 200.000 đồng.")
                .path("status").asText());
        assertEquals("no_results", ask("Tôi cần áo sơ mi, không quá 1 đồng/m.").path("status").asText());
    }

    private JsonNode ask(String query) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/advise"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("query", query))))
                .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode(), response.body());
        return JSON.readTree(response.body());
    }

    private static List<String> ids(JsonNode response) {
        List<String> ids = new ArrayList<>();
        for (JsonNode item : response.path("recommendations")) ids.add(item.at("/fabric/fabric_id").asText());
        return ids;
    }
}
