package com.fabric.fabricaiadvisor.evaluation;

import com.fabric.fabricaiadvisor.dto.FabricCriteria;
import com.fabric.fabricaiadvisor.repository.FabricRepository;
import com.fabric.fabricaiadvisor.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

/** Opt in explicitly. Requires running native model servers; excluded from ordinary test runs. */
@EnabledIfSystemProperty(named = "live.eval", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(LiveAiEvaluationIT.DiagnosticConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LiveAiEvaluationIT {
    private static final Path DATABASE = Path.of("fabric.db").toAbsolutePath();
    private static final Path CONFIG = Path.of(System.getProperty("live.models.file", "ai-models.json")).toAbsolutePath();
    private static final ObjectMapper JSON = new ObjectMapper();
    @LocalServerPort private int port;
    @Autowired private AiModelRegistry registry;
    @Autowired private FabricRepository repository;
    @Autowired private FabricRecommendationService ranking;
    @Autowired private FabricValidationService validator;
    @Autowired private CapturingExplanations explanations;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        if (!Files.isRegularFile(DATABASE) || !Files.isRegularFile(CONFIG))
            throw new IllegalStateException("Run from the project root with fabric.db and ai-models.json present.");
        properties.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE.toUri().toASCIIString() + "?mode=ro");
        properties.add("ai.models-file", CONFIG::toString);
        properties.add("server.address", () -> "127.0.0.1");
    }

    @Test
    void evaluateActualConfiguredModelsAndWriteReport() throws Exception {
        Path output = Path.of("target", "live-evaluation", Instant.now().toString().replace(':', '-')
                + "-" + UUID.randomUUID().toString().substring(0, 8));
        Files.createDirectories(output);
        Map<String, Object> report = new LinkedHashMap<>();
        List<Map<String, Object>> advice = new ArrayList<>(), evaluation = new ArrayList<>();
        boolean adviceOnly = Boolean.getBoolean("live.adviceOnly");
        report.put("startedAt", OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString());
        report.put("labelStatus", "proposed_requires_human_review");
        report.put("runScope", adviceOnly ? "advice_only" : "advice_and_independent_evaluator");
        report.put("evaluationScope", adviceOnly ? "not_run_advice_only" : "model_only_after_structural_rules");
        report.put("draftDiagnostics", "test_only_capture_not_api_output");
        report.put("adviceSummarySource", "catalog_template; draftAttempts capture model output before summary rendering");
        report.put("workload", "sequential_single_client");
        report.put("runLabel", System.getProperty("live.run.label", "configured_models"));
        report.put("advice", advice); report.put("evaluation", evaluation);
        report.put("completed", false);
        try {
            JsonNode adviceSet = resource("advice-cases.json"), evaluationSet = resource("evaluator-cases.json");
            String databaseHash = sha256(DATABASE), configHash = sha256(CONFIG);
            report.put("catalogSha256", databaseHash); report.put("configSha256", configHash);
            if (!databaseHash.equals(adviceSet.path("catalogSha256").asText())
                    || !databaseHash.equals(evaluationSet.path("catalogSha256").asText()))
                throw new IllegalStateException("Catalog changed. Review case labels before running this dataset.");
            report.put("adviceDatasetSha256", sha256(resourceBytes("advice-cases.json")));
            report.put("evaluationDatasetSha256", sha256(resourceBytes("evaluator-cases.json")));
            var selected = registry.snapshot();
            String revision = selected.revision();
            if (!revision.equals(JSON.readTree(Files.readString(CONFIG)).path("revision").asText()))
                throw new IllegalStateException("Requested model configuration was not loaded; refusing environment fallback.");
            if (selected.evaluation() == null) throw new IllegalStateException("Select an evaluation model first.");
            selected.main().checkAvailability(); selected.evaluation().checkAvailability();
            report.put("revision", revision);
            report.put("mainModel", selected.main().getModelName());
            report.put("evaluationModel", selected.evaluation().getModelName());
            report.put("identityLimit", "Aliases and config hash only; GGUF weights and llama.cpp build are not identified by these values.");
            int repeats = Integer.parseInt(System.getProperty("live.repeats", "1"));
            if (repeats < 1 || repeats > 20) throw new IllegalArgumentException("live.repeats must be between 1 and 20.");
            report.put("repeats", repeats);
            report.put("plannedAdviceCases", adviceSet.path("cases").size() * repeats);
            report.put("plannedEvaluatorCases", adviceOnly ? 0 : evaluationSet.path("cases").size());
            FabricCriteria criteria = new FabricCriteria("recommend_fabric", "sơ mi", new BigDecimal("200000"),
                    true, List.of(), false, null, List.of());
            var sources = ranking.rank(repository.findCandidates(criteria), List.of());
            Map<String, JsonNode> catalog = new LinkedHashMap<>();
            sources.forEach(row -> {
                Map<String, Object> facts = new LinkedHashMap<>(row);
                for (String field : List.of("match_score", "ranking_score", "matched_preferences", "caution_preferences",
                        "unknown_preferences", "recommendation_reason", "ranking_explanation", "scoring_method", "recommendation_note"))
                    facts.remove(field);
                catalog.put(row.get("fabric_id").toString(), JSON.valueToTree(facts));
            });
            report.put("catalogSnapshot", catalog);

            // Record one warm-up separately. It never contributes to reported percentiles.
            assertUnchanged(databaseHash, configHash);
            var warmup = request(adviceSet.path("cases").get(0), catalog, revision);
            report.put("warmup", warmup);
            report.put("warmupContractPassed", warmup.get("contractPassed"));
            requireUsable(warmup);
            assertUnchanged(databaseHash, configHash);
            for (int repeat = 1; repeat <= repeats; repeat++) {
                for (JsonNode testCase : adviceSet.path("cases")) {
                    assertUnchanged(databaseHash, configHash);
                    var item = request(testCase, catalog, revision);
                    item.put("repeat", repeat); advice.add(item);
                    assertUnchanged(databaseHash, configHash);
                    requireUsable(item);
                    System.out.println("LIVE advice " + testCase.path("id").asText() + ": " + item.get("clientElapsedMs") + " ms");
                }
            }
            for (JsonNode testCase : evaluationSet.path("cases")) {
                if (adviceOnly) break;
                assertUnchanged(databaseHash, configHash);
                Map<String, String> reasons = new LinkedHashMap<>();
                testCase.path("reasonsById").properties().forEach(e -> reasons.put(e.getKey(), e.getValue().asText()));
                var draft = new FabricExplanationService.Draft(testCase.path("explanation").asText(), reasons);
                FabricValidationService.Result result;
                long began = System.nanoTime();
                try (var scope = registry.openScope()) {
                    if (!revision.equals(scope.selection().revision())) throw new IllegalStateException("Model revision changed.");
                    result = validator.evaluateModel(testCase.path("query").asText(), criteria, sources, draft);
                    if (!result.rulesPassed())
                        throw new IllegalStateException("Evaluator case failed backend rules; not a model-quality case.");
                }
                var judgement = new LiveEvaluationSupport.Judgement(testCase.path("expectedPass").asBoolean(),
                        result.rulesPassed(), result.evaluationStatus());
                evaluation.add(Map.of("id", testCase.path("id").asText(), "expectedPass", judgement.expectedPass(),
                        "classified", judgement.classified(), "agrees", judgement.agrees(),
                        "elapsedMs", elapsed(began), "result", result.toMap()));
                assertUnchanged(databaseHash, configHash);
                System.out.println("LIVE evaluator " + testCase.path("id").asText() + ": " + result.evaluationStatus());
                if (!judgement.classified()) throw new IllegalStateException("Evaluator unavailable/invalid; stopped before more requests.");
            }
            report.put("completed", true);
        } catch (Exception e) {
            report.put("aborted", e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        } finally {
            report.put("finishedAt", OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString());
            report.put("clientLatencyAll", latency(advice, false));
            report.put("clientLatencySuccessfulFullPipeline", latency(advice, true));
            report.put("contractFailures", advice.stream().filter(r -> !Boolean.TRUE.equals(r.get("contractPassed"))).count());
            var judgements = evaluation.stream().map(row -> {
                @SuppressWarnings("unchecked") var result = (Map<String, Object>) row.get("result");
                return new LiveEvaluationSupport.Judgement((Boolean) row.get("expectedPass"),
                        (Boolean) result.get("rulesPassed"), (String) result.get("evaluationStatus"));
            }).toList();
            report.put("evaluatorAgreement", LiveEvaluationSupport.quality(judgements));
            Files.writeString(output.resolve("report.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report), StandardCharsets.UTF_8);
            Files.writeString(output.resolve("advice.csv"), csv(advice), StandardCharsets.UTF_8);
            System.out.println("LIVE REPORT: " + output.toAbsolutePath());
        }
        assertTrue(Boolean.TRUE.equals(report.get("warmupContractPassed")), "Warmup API contract mismatch: review report.json and draftAttempts");
        assertEquals(0L, report.get("contractFailures"), "API contract mismatches: read report.json");
        if (!adviceOnly) assertTrue(evaluation.stream().allMatch(row -> Boolean.TRUE.equals(row.get("agrees"))),
                "Evaluator disagreed with proposed labels: review report.json and labels; this is not a >95% accuracy claim.");
    }

    private Map<String, Object> request(JsonNode testCase, Map<String, JsonNode> catalog, String revision) throws Exception {
        explanations.clear();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/advise"))
                .timeout(Duration.ofMinutes(22)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("query", testCase.path("query").asText())), StandardCharsets.UTF_8)).build();
        long began = System.nanoTime();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        double duration = elapsed(began);
        JsonNode body = JSON.readTree(response.body());
        var issues = LiveEvaluationSupport.adviceIssues(testCase.path("expected"), body, catalog);
        if (response.statusCode() != 200) issues.add("HTTP " + response.statusCode());
        if (!revision.equals(response.headers().firstValue("X-AI-Config-Revision").orElse("")))
            throw new IllegalStateException("Model revision changed during HTTP request.");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", testCase.path("id").asText()); result.put("query", testCase.path("query").asText());
        result.put("expected", testCase.path("expected")); result.put("httpStatus", response.statusCode());
        result.put("clientElapsedMs", duration); result.put("response", body);
        result.put("draftAttempts", explanations.snapshot());
        result.put("contractPassed", issues.isEmpty()); result.put("issues", issues);
        return result;
    }

    private void assertUnchanged(String databaseHash, String configHash) throws Exception {
        Path wal = Path.of(DATABASE + "-wal");
        if (Files.exists(wal) && Files.size(wal) > 0)
            throw new IllegalStateException("Active SQLite WAL found; stop the writer and review the catalog before evaluation.");
        if (!databaseHash.equals(sha256(DATABASE)) || !configHash.equals(sha256(CONFIG)))
            throw new IllegalStateException("Catalog/config changed during evaluation; partial report only.");
    }

    private static void requireUsable(Map<String, Object> sample) {
        JsonNode body = (JsonNode) sample.get("response");
        @SuppressWarnings("unchecked")
        var attempts = (List<Map<String, Object>>) sample.getOrDefault("draftAttempts", List.of());
        boolean generated = !attempts.isEmpty() && attempts.getLast().containsKey("draft");
        if (!LiveEvaluationSupport.usableResponse(((Number) sample.get("httpStatus")).intValue(), body, generated))
            throw new IllegalStateException("Unavailable/invalid pipeline: status=" + body.path("status").asText()
                    + ", evaluation=" + body.at("/factCheck/evaluationStatus").asText() + ", lastDraftGenerated=" + generated
                    + "; stopped before sending more requests. Review draftAttempts and factCheck.");
    }

    private static Map<String, Object> latency(List<Map<String, Object>> samples, boolean fullOnly) {
        return LiveEvaluationSupport.latency(samples.stream().filter(row -> !fullOnly
                        || ((JsonNode) row.get("response")).path("status").asText().equals("success"))
                .map(row -> ((Number) row.get("clientElapsedMs")).doubleValue()).toList());
    }

    private static double elapsed(long began) { return (System.nanoTime() - began) / 1_000_000.0; }
    private static JsonNode resource(String name) throws Exception { return JSON.readTree(resourceBytes(name)); }
    private static byte[] resourceBytes(String name) throws Exception {
        try (var input = LiveAiEvaluationIT.class.getResourceAsStream("/evaluation/" + name)) {
            if (input == null) throw new IllegalStateException("Missing evaluation dataset: " + name);
            return input.readAllBytes();
        }
    }
    private static String sha256(Path file) throws Exception { return sha256(Files.readAllBytes(file)); }
    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static String csv(List<Map<String, Object>> samples) {
        StringBuilder text = new StringBuilder("id,repeat,http_status,status,client_ms,service_ms,intent_ms,database_ms,ranking_ms,evidence_ms,explanation_ms,validation_ms,contract_passed\n");
        for (var row : samples) {
            JsonNode response = (JsonNode) row.get("response");
            text.append(row.get("id")).append(',').append(row.get("repeat")).append(',').append(row.get("httpStatus"))
                    .append(',').append(response.path("status").asText()).append(',').append(row.get("clientElapsedMs"))
                    .append(',').append(response.at("/timings/totalMs").asText());
            for (String stage : List.of("intent", "database", "ranking", "evidence", "explanation", "validation"))
                text.append(',').append(response.at("/timings/stages/" + stage + "/durationMs").asText());
            text.append(',').append(row.get("contractPassed")).append('\n');
        }
        return text.toString();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DiagnosticConfig {
        @Bean @Primary
        CapturingExplanations captureDrafts(AiModelClient client, ObjectMapper mapper) {
            return new CapturingExplanations(client, mapper);
        }
    }

    /** Enabled only inside this opt-in test. Rejected drafts never enter the public API output. */
    static class CapturingExplanations extends FabricExplanationService {
        private final List<Map<String, Object>> events = Collections.synchronizedList(new ArrayList<>());
        CapturingExplanations(AiModelClient client, ObjectMapper mapper) { super(client, mapper); }
        void clear() { events.clear(); }
        List<Map<String, Object>> snapshot() { synchronized (events) { return List.copyOf(events); } }
        private Draft record(String operation, Draft previous, List<String> issues, Supplier<Draft> call) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("operation", operation); event.put("validationIssues", List.copyOf(issues));
            if (previous != null) event.put("previousDraft", previous);
            try {
                Draft draft = call.get(); event.put("draft", draft); return draft;
            } catch (RuntimeException e) {
                event.put("error", e.getClass().getSimpleName() + ": " + e.getMessage()); throw e;
            } finally { events.add(Collections.unmodifiableMap(new LinkedHashMap<>(event))); }
        }
        @Override public Draft generate(String query, FabricCriteria criteria, List<Map<String, Object>> rows) {
            return record("generate", null, List.of(), () -> super.generate(query, criteria, rows));
        }
        @Override public Draft generate(String query, FabricCriteria criteria, List<Map<String, Object>> rows, Map<String, Object> preferences) {
            return record("generate", null, List.of(), () -> super.generate(query, criteria, rows, preferences));
        }
        @Override public Draft correct(String query, FabricCriteria criteria, List<Map<String, Object>> rows, Draft previous, List<String> issues) {
            return record("correct", previous, issues, () -> super.correct(query, criteria, rows, previous, issues));
        }
        @Override public Draft correct(String query, FabricCriteria criteria, List<Map<String, Object>> rows, Draft previous, List<String> issues, Map<String, Object> preferences) {
            return record("correct", previous, issues, () -> super.correct(query, criteria, rows, previous, issues, preferences));
        }
    }
}
