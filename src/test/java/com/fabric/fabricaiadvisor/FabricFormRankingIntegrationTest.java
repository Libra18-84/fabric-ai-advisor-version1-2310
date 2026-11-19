package com.fabric.fabricaiadvisor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FabricFormRankingIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path DATABASE = database();
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    private final HttpClient http = HttpClient.newHttpClient();

    private static Path database() {
        try {
            Path file = Files.createTempFile("fabric-form-ranking-", ".db");
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
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE);
        properties.add("ai.models-file", () -> "");
    }

    @BeforeEach
    void seedCatalog() {
        jdbc.update("DELETE FROM fabric_database");
        row("FAB001", "Cotton", "Áo sơ mi", "Thoáng khí, thấm hút; có thể co sau giặt", 45000, 90000);
        row("FAB005", "Kate", "Sơ mi, đồng phục", "Ít nhăn hơn cotton thuần tùy tỷ lệ pha", 30000, 55000);
        row("FAB006", "Linen", "Sơ mi mùa nóng", "Thoáng; dễ nhăn, độ rũ phụ thuộc thành phần", 85000, 160000);
    }

    private void row(String id, String name, String garments, String description, int min, int max) {
        jdbc.update("""
                INSERT INTO fabric_database (fabric_id,fabric_name,currency,construction,
                  recommended_garments,properties_and_cautions,price_min_vnd_per_m,
                  price_max_vnd_per_m,verification_status,source_url)
                VALUES (?,?,'VND','Dệt thoi',?,?,?,?,'unverified',?)
                """, id, name, garments, description, min, max, "https://example.test/" + id);
    }

    private JsonNode recommend(String preference, String requirements) throws Exception {
        String url = "http://127.0.0.1:" + port + "/api/recommendations?garment="
                + URLEncoder.encode("sơ mi", StandardCharsets.UTF_8) + "&budget=200000&preference="
                + URLEncoder.encode(preference, StandardCharsets.UTF_8) + "&requirements="
                + URLEncoder.encode(requirements, StandardCharsets.UTF_8);
        var reply = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, reply.statusCode());
        return JSON.readTree(reply.body());
    }

    private List<String> ids(JsonNode rows) {
        List<String> result = new ArrayList<>();
        rows.forEach(row -> result.add(row.path("fabric_id").asText()));
        return result;
    }

    @Test
    void neutralizedPreferenceDoesNotKeepPointsOrEvidence() throws Exception {
        var reply = recommend("thoáng mát", "không cần thoáng mát");
        assertEquals("rule_based", reply.path("status").asText());
        assertTrue(reply.at("/criteria/preferences").isEmpty());
        assertEquals("", reply.path("combined_preference").asText());
        assertEquals(List.of("FAB001", "FAB005", "FAB006"), ids(reply.path("fabrics")));
        for (JsonNode row : reply.path("fabrics")) {
            assertEquals(0, row.path("ranking_score").asInt());
            assertTrue(row.path("matched_preferences").isEmpty());
            assertTrue(row.path("caution_preferences").isEmpty());
            assertTrue(row.path("unknown_preferences").isEmpty());
        }
    }

    @Test
    void preferenceRecognizedFromNaturalTextAffectsRanking() throws Exception {
        var reply = recommend("ưu tiên thoáng mát", "");
        var rows = reply.path("fabrics");
        assertEquals(List.of("FAB001", "FAB006", "FAB005"), ids(rows));
        assertEquals(1, rows.get(0).path("ranking_score").asInt());
        assertEquals(1, rows.get(1).path("ranking_score").asInt());
        assertEquals("thoáng mát", rows.get(0).path("matched_preferences").get(0).asText());
        assertEquals(0, rows.get(2).path("ranking_score").asInt());
    }

    @Test
    void neutralizingOnePreferencePreservesTheOtherPreference() throws Exception {
        var reply = recommend("thoáng mát, thấm hút", "không cần thoáng mát");
        assertEquals("thấm hút", reply.path("combined_preference").asText());
        assertEquals(List.of("FAB001", "FAB005", "FAB006"), ids(reply.path("fabrics")));
        assertEquals(1, reply.at("/fabrics/0/ranking_score").asInt());
        assertEquals("thấm hút", reply.at("/fabrics/0/matched_preferences/0").asText());
        for (JsonNode row : reply.path("fabrics")) {
            assertFalse(row.path("matched_preferences").toString().contains("thoáng mát"));
            assertFalse(row.path("unknown_preferences").toString().contains("thoáng mát"));
        }
    }

    @Test
    void mandatoryEvidenceStillFiltersAndScoresTheEligibleRows() throws Exception {
        var reply = recommend("", "bắt buộc thoáng mát");
        assertEquals("rule_based", reply.path("status").asText());
        assertTrue(reply.at("/criteria/preferences").isEmpty());
        assertEquals("thoáng mát", reply.at("/criteria/requiredProperties/0").asText());
        assertEquals(List.of("FAB001", "FAB006"), ids(reply.path("fabrics")));
        for (JsonNode row : reply.path("fabrics")) assertEquals(1, row.path("ranking_score").asInt());
    }

    @Test
    void mandatoryUnknownEvidenceRemainsUnconfirmed() throws Exception {
        var reply = recommend("", "bắt buộc mềm");
        assertEquals("insufficient_data", reply.path("status").asText());
        assertTrue(reply.path("fabrics").isEmpty());
        assertEquals(3, reply.path("candidates").size());
        assertEquals("mềm", reply.path("missingProperties").get(0).asText());
        for (JsonNode row : reply.path("candidates")) assertTrue(row.path("matched_preferences").isEmpty());
    }

    @Test
    void noPreferenceStillReturnsZeroScores() throws Exception {
        var reply = recommend("", "");
        assertEquals("rule_based", reply.path("status").asText());
        assertEquals(3, reply.path("total").asInt());
        for (JsonNode row : reply.path("fabrics")) assertEquals(0, row.path("ranking_score").asInt());
    }
}
