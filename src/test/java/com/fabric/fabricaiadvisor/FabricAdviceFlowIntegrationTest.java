package com.fabric.fabricaiadvisor;

import com.fabric.fabricaiadvisor.dto.FabricCriteria;
import com.fabric.fabricaiadvisor.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FabricAdviceFlowIntegrationTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FabricAdviceFlowIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path DATABASE = database();
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MainStub main;
    @Autowired private EvaluationStub evaluation;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final String QUERY = "Tôi cần vải may áo sơ mi, không quá 200000 đồng/m.";

    private static Path database() {
        try {
            Path file = Files.createTempFile("fabric-advice-flow-", ".db");
            file.toFile().deleteOnExit();
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                 var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE fabric_database (
                          fabric_id TEXT PRIMARY KEY, fabric_name TEXT, currency TEXT,
                          fiber_composition TEXT, construction TEXT, gsm_range TEXT, typical_width TEXT,
                          price_min_vnd_per_m REAL, price_max_vnd_per_m REAL, recommended_garments TEXT,
                          properties_and_cautions TEXT, verification_status TEXT, source_url TEXT)
                        """);
            }
            return file;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE);
        registry.add("ai.models-file", () -> "");
        registry.add("ai.evaluation.model", () -> "");
    }

    @TestConfiguration
    static class Config {
        @Bean @Primary MainStub mainForTest() { return new MainStub(); }
        @Bean EvaluationStub evaluatorForTest() { return new EvaluationStub(); }
        @Bean @Primary FabricValidationService validatorForTest(ObjectMapper mapper, EvaluationStub evaluator) {
            return new FabricValidationService(mapper, evaluator);
        }
    }

    static class MainStub implements AiModelClient {
        volatile String mode = "normal", lastIntent = "", lastExplanation = "";
        final AtomicInteger explanations = new AtomicInteger();
        public String getModelName() { return "test-main"; }
        public String generateJson(String prompt, String input) {
            if (prompt.contains("Bạn trích xuất")) {
                lastIntent = input;
                if (mode.equals("intent_offline")) throw new ResourceAccessException("offline");
                if (mode.equals("intent_invalid")) return "{\"intent\":true}";
                String query = input.startsWith("{") ? JSON.readTree(input).path("query").asText() : input;
                List<String> soft = new ArrayList<>(), required = new ArrayList<>();
                String property = query.contains("không co giãn") ? "không co giãn"
                        : query.contains("chống cháy") ? "chống cháy" : query.contains("mềm") ? "mềm" : null;
                if (mode.equals("flip")) soft.add("co giãn");
                else if (property != null) {
                    if (query.contains("bắt buộc") || property.startsWith("không ")) required.add(property);
                    else soft.add(property);
                }
                return JSON.writeValueAsString(new FabricCriteria("recommend_fabric", "sơ mi",
                        mode.equals("wrong_price") ? new BigDecimal("100000") : query.contains("1 đồng") ? BigDecimal.ONE : new BigDecimal("200000"),
                        true, soft, false, null, required));
            }
            explanations.incrementAndGet();
            lastExplanation = input;
            if (mode.equals("main_offline")) throw new ResourceAccessException("offline");
            if (mode.equals("main_invalid")) return "not json";
            List<Map<String, String>> selected = new ArrayList<>();
            for (JsonNode row : JSON.readTree(input).path("fabrics")) {
                String reason = "Theo mô tả nguồn: " + row.path("properties_and_cautions").asText();
                if (row.path("fabric_id").asText().equals("F2") && (mode.equals("unsupported_claim")
                        || mode.equals("repair_claim") && explanations.get() == 1)) reason = "Vải mềm mại theo catalog.";
                if (mode.equals("implicit_units")) reason = "Giá từ " + row.path("price_min_vnd_per_m").asText()
                        + " VND đến " + row.path("price_max_vnd_per_m").asText() + " VND.";
                if (row.path("fabric_id").asText().equals("F1")) {
                    reason = switch (mode) {
                        case "fact_price" -> "Giá catalog là 10.000–20.000 VND/m.";
                        case "fact_composition" -> "Thành phần là 100% polyester.";
                        case "fact_source" -> "Nguồn catalog là https://invented.invalid/catalog.";
                        case "fact_verification" -> "Dữ liệu đã được xác minh độc lập.";
                        case "repair_fact" -> explanations.get() == 1 ? "Giá catalog là 10.000–20.000 VND/m." : reason;
                        default -> reason;
                    };
                }
                selected.add(Map.of("fabricId", row.path("fabric_id").asText(), "reason", reason));
                if (mode.equals("subset")) break;
            }
            if (mode.equals("unknown_id")) selected.set(0, Map.of("fabricId", "OUTSIDE", "reason", "Ngoài nguồn"));
            if (mode.equals("duplicate")) selected.set(1, selected.getFirst());
            String summary = mode.equals("bad_summary") ? "Dữ liệu đã được xác minh độc lập; mọi vải có giá 45.000–160.000 VND/m."
                    : "Theo dữ liệu catalog, chưa xác minh độc lập.";
            return JSON.writeValueAsString(Map.of("explanation", summary,
                    "recommendations", selected));
        }
    }

    static class EvaluationStub implements AiModelClient {
        volatile String mode = "normal", lastInput = "";
        final AtomicInteger calls = new AtomicInteger();
        public String getModelName() { return "test-evaluator:v1"; }
        public String generateJson(String prompt, String input) {
            lastInput = input;
            int attempt = calls.incrementAndGet();
            if (mode.equals("offline")) throw new ResourceAccessException("offline");
            if (mode.equals("invalid")) return "{\"passed\":true,\"issues\":[\"Contradiction\"]}";
            boolean passed = !mode.equals("reject") && !(mode.equals("repair") && attempt == 1);
            return JSON.writeValueAsString(Map.of("passed", passed,
                    "issues", passed ? List.of() : List.of("Cần sửa giải thích theo nguồn.")));
        }
    }

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM fabric_database");
        row("F1", "Cotton", "Áo sơ mi", "Thoáng khí; mềm mại; không co giãn", 45000, 90000);
        row("F2", "Kate", "Sơ mi", "Chưa xác minh độ mềm; ít nhăn; co giãn", 30000, 55000);
        row("F3", "Denim", "Quần jean", "Bền", 55000, 120000);
        row("F4", "Linen", "Sơ mi mùa nóng, quần rộng", "Thoáng; dễ nhăn", 85000, 160000);
        main.mode = "normal"; main.explanations.set(0); main.lastIntent = ""; main.lastExplanation = "";
        evaluation.mode = "normal"; evaluation.calls.set(0); evaluation.lastInput = "";
    }

    private void row(String id, String name, String garment, String description, int min, int max) {
        jdbc.update("""
                INSERT INTO fabric_database (fabric_id,fabric_name,currency,construction,
                  recommended_garments,properties_and_cautions,price_min_vnd_per_m,
                  price_max_vnd_per_m,verification_status,source_url)
                VALUES (?,?,'VND','Dệt thoi',?,?,?,?,'unverified',?)
                """, id, name, garment, description, min, max, "https://example.test/" + id);
    }

    private record Reply(int code, JsonNode json) {}
    private Reply request(String path, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20));
        if (body != null) builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8));
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Reply(response.statusCode(), JSON.readTree(response.body()));
    }
    private Reply advise(String query) throws Exception { return request("/api/advise", Map.of("query", query)); }
    private List<String> recommended(Reply reply) {
        List<String> ids = new ArrayList<>();
        for (JsonNode row : reply.json().path("recommendations")) ids.add(row.path("fabric").path("fabric_id").asText());
        return ids;
    }
    private Map<String, Object> criteria(List<String> hard, int budget, boolean inclusive) {
        return Map.of("intent", "recommend_fabric", "garment", "Áo sơ mi", "maxPriceVndPerMeter", budget,
                "budgetInclusive", inclusive, "preferences", List.of(), "needsClarification", false,
                "requiredProperties", hard);
    }
    @Test
    void serviceTimingsCoverSuccessfulRequestAndDoNotLeakIntoNextEarlyReturn() throws Exception {
        var completed = advise(QUERY).json().path("timings");
        assertEquals("advice_service", completed.path("scope").asText());
        assertEquals("ms", completed.path("unit").asText());
        assertTrue(completed.path("totalMs").asDouble() >= 0);
        double sum = 0;
        for (String name : List.of("intent", "database", "ranking", "evidence", "explanation", "validation")) {
            var stage = completed.path("stages").path(name);
            assertTrue(stage.path("calls").asInt() > 0, name);
            assertTrue(stage.path("durationMs").asDouble() >= 0, name);
            sum += stage.path("durationMs").asDouble();
        }
        assertTrue(sum <= completed.path("totalMs").asDouble() + 0.01);
        var early = advise("Sơ mi bắt buộc chống cháy, không quá 200000 đồng/m").json().path("timings");
        assertEquals(1, early.path("stages").path("intent").path("calls").asInt());
        assertEquals(0, early.path("stages").path("explanation").path("calls").asInt());
        assertEquals(0, early.path("stages").path("validation").path("calls").asInt());
    }

    @Test
    void timingsIncludeCorrectionAndFailedModelAttempts() throws Exception {
        evaluation.mode = "repair";
        var repaired = advise(QUERY).json();
        assertEquals("success", repaired.path("status").asText());
        assertEquals(2, repaired.at("/timings/stages/explanation/calls").asInt());
        assertEquals(2, repaired.at("/timings/stages/validation/calls").asInt());
        evaluation.mode = "normal";
        main.mode = "main_offline";
        var fallback = advise(QUERY).json();
        assertEquals("fallback", fallback.path("status").asText());
        assertEquals(1, fallback.at("/timings/stages/explanation/calls").asInt());
        assertEquals(1, fallback.at("/timings/stages/validation/calls").asInt());
        main.mode = "intent_offline";
        var failedIntent = advise(QUERY).json();
        assertEquals(1, failedIntent.at("/timings/stages/intent/calls").asInt());
        assertEquals(0, failedIntent.at("/timings/stages/database/calls").asInt());
    }


    @Test
    void originalStrictPriceOverridesTheModelsInclusiveBudgetBeforeSql() throws Exception {
        var result = advise("Tôi cần sơ mi, dưới 90.000 đồng/m.");
        assertEquals("success", result.json().path("status").asText());
        assertEquals(List.of("F2"), recommended(result));
        assertFalse(result.json().at("/criteria/budgetInclusive").asBoolean());
        assertEquals(90000, result.json().at("/criteria/maxPriceVndPerMeter").asInt());
    }

    @Test
    void oneDongCannotBecomeOneHundredThousandAndTotalMoneyCannotBecomeUnitPrice() throws Exception {
        main.mode = "wrong_price";
        var one = advise("Sơ mi tối đa 1 đồng/m").json();
        assertEquals("no_results", one.path("status").asText());
        assertEquals(1, one.at("/criteria/maxPriceVndPerMeter").asInt());
        assertEquals(0, main.explanations.get());
        var total = advise("Sơ mi, tổng ngân sách mua vải là 200.000 đồng").json();
        assertEquals("needs_clarification", total.path("status").asText());
        assertEquals(0, total.at("/timings/stages/database/calls").asInt());
        assertEquals(0, main.explanations.get());
        assertEquals(0, evaluation.calls.get());
    }

    @Test
    void unsupportedPropertyCannotBecomeSuccessfulAdviceFromAnAlwaysPassingEvaluator() throws Exception {
        main.mode = "unsupported_claim";
        var reply = advise(QUERY).json();
        assertEquals("validation_failed", reply.path("status").asText());
        assertTrue(reply.path("recommendations").isEmpty());
        assertEquals(2, main.explanations.get());
        assertEquals(0, evaluation.calls.get());
    }

    @Test
    void unsupportedPropertyCanBeCorrectedOnceBeforeTheEvaluatorRuns() throws Exception {
        main.mode = "repair_claim";
        var reply = advise(QUERY).json();
        assertEquals("success", reply.path("status").asText());
        assertEquals(2, reply.path("explanationAttempts").asInt());
        assertEquals(1, evaluation.calls.get());
    }

    @Test
    void inventedCatalogFactsNeverReachUsersEvenWhenTheEvaluatorAlwaysPasses() throws Exception {
        for (String mode : List.of("fact_price", "fact_composition", "fact_source", "fact_verification")) {
            main.mode = mode; main.explanations.set(0); evaluation.calls.set(0);
            var reply = advise(QUERY);
            assertEquals("validation_failed", reply.json().path("status").asText(), mode);
            assertTrue(recommended(reply).isEmpty());
            assertEquals(2, main.explanations.get()); assertEquals(0, evaluation.calls.get());
            assertFalse(reply.json().path("factCheck").path("validated").asBoolean());
            assertFalse(reply.json().path("explanation").asText().contains("10.000"));
            assertFalse(reply.json().path("explanation").asText().contains("invented.invalid"));
        }
    }

    @Test
    void factIssuesAreForwardedForOneCorrectionBeforeIndependentEvaluation() throws Exception {
        main.mode = "repair_fact";
        var reply = advise(QUERY);
        assertEquals("success", reply.json().path("status").asText());
        assertEquals(2, main.explanations.get()); assertEquals(1, evaluation.calls.get());
        assertTrue(JSON.readTree(main.lastExplanation).path("validationIssues").size() > 0);
        assertTrue(reply.json().path("factCheck").path("validated").asBoolean());
        assertFalse(reply.json().path("explanation").asText().contains("10.000"));
    }

    @Test
    void publicAndEvaluatorSummariesAreBuiltFromSelectionInsteadOfUntrustedModelOverview() throws Exception {
        main.mode = "bad_summary";
        var reply = advise(QUERY);
        assertEquals("success", reply.json().path("status").asText());
        assertTrue(reply.json().path("explanation").asText().contains("3 loại vải"));
        assertFalse(reply.json().path("explanation").asText().contains("45.000"));
        assertFalse(reply.json().path("explanation").asText().contains("đã được xác minh độc lập"));
        assertEquals(reply.json().path("explanation").asText(), JSON.readTree(evaluation.lastInput).at("/draft/explanation").asText());
        assertEquals(1, main.explanations.get()); assertEquals(1, evaluation.calls.get());
    }

    @Test
    void correctPricesCanOmitTheRepeatedMeterUnitInReasonsBoundToOneSqlRow() throws Exception {
        main.mode = "implicit_units";
        var reply = advise(QUERY);
        assertEquals("success", reply.json().path("status").asText());
        assertEquals(3, recommended(reply).size());
        assertEquals(1, main.explanations.get()); assertEquals(1, evaluation.calls.get());
    }

    @Test
    void liveSqlChangesAffectBothChatEndpointsWithoutTraining() throws Exception {
        assertEquals(List.of("F1", "F2", "F4"), recommended(advise(QUERY)));
        jdbc.update("UPDATE fabric_database SET price_max_vnd_per_m=250000 WHERE fabric_id='F1'");
        var latest = advise(QUERY);
        assertEquals(List.of("F2", "F4"), recommended(latest));
        assertEquals(recommended(latest), recommended(request("/api/ai/ask", Map.of("question", QUERY))));
        assertFalse(main.lastExplanation.contains("\"fabric_id\":\"F1\""));
    }

    @Test
    void validSubsetIsAcceptedAndOnlySelectedSqlRowsAreEvaluated() throws Exception {
        main.mode = "subset";
        var reply = advise(QUERY);
        assertEquals("success", reply.json().path("status").asText());
        assertEquals(List.of("F1"), recommended(reply));
        assertEquals(1, JSON.readTree(evaluation.lastInput).path("sources").size());
        assertTrue(reply.json().path("factCheck").path("validated").asBoolean());
    }

    @Test
    void unknownAndDuplicateIdsAreRejectedWithBoundedRetry() throws Exception {
        for (String mode : List.of("unknown_id", "duplicate")) {
            main.mode = mode; main.explanations.set(0);
            var reply = advise(QUERY);
            assertEquals("validation_failed", reply.json().path("status").asText());
            assertTrue(recommended(reply).isEmpty());
            assertEquals(2, main.explanations.get());
        }
        assertEquals(0, evaluation.calls.get());
    }

    @Test
    void mandatoryPropertyExcludesUnknownAndContradictingRows() throws Exception {
        var reply = advise("Sơ mi bắt buộc mềm, không quá 200000 đồng/m");
        assertEquals(List.of("F1"), recommended(reply));
        assertEquals("mềm", reply.json().path("criteria").path("requiredProperties").get(0).asText());
        assertEquals(1, JSON.readTree(main.lastExplanation).path("fabrics").size());
    }

    @Test
    void missingMandatoryEvidenceReturnsUnconfirmedCandidatesWithoutGeneration() throws Exception {
        var reply = advise("Sơ mi bắt buộc chống cháy, không quá 200000 đồng/m");
        assertEquals("insufficient_data", reply.json().path("status").asText());
        assertTrue(recommended(reply).isEmpty());
        assertEquals(3, reply.json().path("candidates").size());
        assertEquals(0, main.explanations.get()); assertEquals(0, evaluation.calls.get());
        assertFalse(reply.json().path("factCheck").path("validated").asBoolean());
    }

    @Test
    void reversedNegationStopsBeforeSqlAdvice() throws Exception {
        main.mode = "flip";
        var reply = advise("Sơ mi không co giãn, không quá 200000 đồng/m");
        assertEquals("needs_clarification", reply.json().path("status").asText());
        assertEquals(0, main.explanations.get()); assertEquals(0, evaluation.calls.get());
    }

    @Test
    void allCandidatesContradictingMandatoryConditionReturnsNoResults() throws Exception {
        jdbc.update("UPDATE fabric_database SET properties_and_cautions='Co giãn'");
        var reply = advise("Sơ mi bắt buộc không co giãn, không quá 200000 đồng/m");
        assertEquals("no_results", reply.json().path("status").asText());
        assertEquals(0, main.explanations.get());
    }

    @Test
    void contextIsValidatedForwardedAndEchoed() throws Exception {
        var reply = request("/api/advise", Map.of("query", QUERY,
                "userPreferences", Map.of("language", "vi", "context", "casual")));
        assertEquals(200, reply.code());
        assertEquals("casual", reply.json().path("userPreferences").path("context").asText());
        assertEquals("casual", JSON.readTree(main.lastIntent).path("userPreferences").path("context").asText());
        assertEquals("casual", JSON.readTree(main.lastExplanation).path("userPreferences").path("context").asText());
        assertEquals(400, request("/api/advise", Map.of("query", QUERY, "userPreferences", Map.of("context", 42))).code());
    }

    @Test
    void apiAliasesKeepSqlFactsAndDoNotInventConfidenceOrSinglePrice() throws Exception {
        var reply = advise(QUERY);
        var item = reply.json().path("recommendations").get(0);
        var fabric = item.path("fabric");
        assertEquals(fabric.path("fabric_id").asText(), fabric.path("id").asText());
        assertEquals("Cotton", fabric.path("name").asText());
        assertEquals("unverified", fabric.path("verification_status").asText());
        assertEquals("https://example.test/F1", fabric.path("source").asText());
        assertTrue(fabric.path("price").isNull());
        assertTrue(item.has("confidence")); assertTrue(item.path("confidence").isNull());
        assertEquals("not_calibrated", item.path("confidenceStatus").asText());
        assertEquals("test-evaluator:v1", reply.json().path("factCheck").path("modelVersion").asText());
    }

    @Test
    void oldSearchAndPreviewUseTheSameNormalizationAndBudgetRules() throws Exception {
        String garment = URLEncoder.encode("Áo sơ mi", StandardCharsets.UTF_8);
        var old = request("/api/fabrics/search?garment=" + garment + "&budget=200000", null);
        var preview = request("/api/fabrics/preview", criteria(List.of(), 200000, true));
        assertEquals(3, old.json().path("total").asInt());
        Set<String> ids = new HashSet<>();
        for (JsonNode row : preview.json().path("fabrics")) ids.add(row.path("fabric_id").asText());
        assertEquals(Set.of("F1", "F2", "F4"), ids);
        assertEquals(1, request("/api/fabrics/preview", criteria(List.of(), 90000, false)).json().path("total").asInt());
        assertEquals(2, request("/api/fabrics/preview", criteria(List.of(), 90000, true)).json().path("total").asInt());
        String trousers = URLEncoder.encode("quần tây", StandardCharsets.UTF_8);
        assertEquals(0, request("/api/fabrics/search?garment=" + trousers + "&budget=200000", null).json().path("total").asInt());
    }

    @Test
    void previewAndManualFormAlsoEnforceMandatoryProperties() throws Exception {
        var preview = request("/api/fabrics/preview", criteria(List.of("mềm"), 200000, true));
        assertEquals(1, preview.json().path("total").asInt());
        String needs = URLEncoder.encode("bắt buộc mềm", StandardCharsets.UTF_8);
        var manual = request("/api/recommendations?garment=ao_so_mi&budget=200000&requirements=" + needs, null);
        assertEquals(1, manual.json().path("total").asInt());
    }

    @Test
    void repairIsRequestedOnceWithValidationIssues() throws Exception {
        evaluation.mode = "repair";
        var reply = advise(QUERY);
        assertEquals("success", reply.json().path("status").asText());
        assertEquals(2, main.explanations.get()); assertEquals(2, evaluation.calls.get());
        assertFalse(JSON.readTree(main.lastExplanation).path("validationIssues").isEmpty());
        assertTrue(JSON.readTree(main.lastExplanation).has("previousDraft"));
    }

    @Test
    void repeatedRejectionOrInvalidEvaluatorCannotBeValidated() throws Exception {
        for (String mode : List.of("reject", "invalid")) {
            evaluation.mode = mode; evaluation.calls.set(0); main.explanations.set(0);
            var reply = advise(QUERY);
            assertEquals("validation_failed", reply.json().path("status").asText());
            assertFalse(reply.json().path("factCheck").path("validated").asBoolean());
            assertTrue(main.explanations.get() <= 2);
        }
    }

    @Test
    void modelOutagesHaveSafeFallbackStatuses() throws Exception {
        evaluation.mode = "offline";
        var reply = advise(QUERY);
        assertEquals("fallback", reply.json().path("status").asText());
        assertFalse(reply.json().path("factCheck").path("validated").asBoolean());
        assertEquals("unavailable", reply.json().path("factCheck").path("evaluationStatus").asText());
        evaluation.mode = "normal"; main.mode = "main_offline";
        assertEquals("fallback", advise(QUERY).json().path("status").asText());
        main.mode = "intent_offline";
        assertEquals("needs_clarification", advise(QUERY).json().path("status").asText());
    }

    @Test
    void malformedModelOutputsNeverProduceSuccess() throws Exception {
        main.mode = "intent_invalid";
        assertEquals("needs_clarification", advise(QUERY).json().path("status").asText());
        main.mode = "main_invalid";
        assertEquals("validation_failed", advise(QUERY).json().path("status").asText());
    }

    @Test
    void databaseErrorsHaveTheSame503PayloadAcrossEndpoints() throws Exception {
        jdbc.execute("ALTER TABLE fabric_database RENAME TO temporarily_unavailable");
        try {
            var replies = List.of(request("/api/fabrics", null),
                    request("/api/fabrics/search?garment=so_mi&budget=200000", null),
                    request("/api/recommendations?garment=so_mi&budget=200000", null),
                    request("/api/fabrics/preview", criteria(List.of(), 200000, true)), advise(QUERY));
            for (var reply : replies) {
                assertEquals(503, reply.code());
                assertEquals("DATABASE_UNAVAILABLE", reply.json().path("code").asText());
                assertEquals("Không đọc được dữ liệu SQLite.", reply.json().path("error").asText());
                assertEquals(replies.getFirst().json(), reply.json());
            }
        } finally { jdbc.execute("ALTER TABLE temporarily_unavailable RENAME TO fabric_database"); }
    }
}
