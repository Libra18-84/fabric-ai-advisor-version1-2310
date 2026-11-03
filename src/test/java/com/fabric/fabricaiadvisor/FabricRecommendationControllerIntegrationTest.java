
package com.fabric.fabricaiadvisor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class FabricRecommendationControllerIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient httpClient =
            HttpClient.newHttpClient();

    private String encode(String value) {
        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }

    private HttpResponse<String> callApi(
            String garment,
            String budget,
            String preference,
            String requirements
    ) throws IOException, InterruptedException {

        String url = "http://localhost:" + port
                + "/api/recommendations"
                + "?garment=" + encode(garment)
                + "&budget=" + encode(budget)
                + "&preference=" + encode(preference)
                + "&requirements=" + encode(requirements);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8
                )
        );
    }

    @Test
    void testNormalizeDuplicatePreferences()
            throws IOException, InterruptedException {

        HttpResponse<String> response = callApi(
                "sơ mi",
                "200000",
                "mềm mại, thoáng khí",
                "Tôi muốn vải mềm, thoáng mát"
        );

        assertEquals(200, response.statusCode());

        String json = response.body();

        assertTrue(
                json.contains(
                        "\"combined_preference\":\"mềm, thoáng mát\""
                ),
                "API phai chuan hoa va loai bo so thich trung nghia"
        );

        assertTrue(
                json.contains("\"extracted_preferences\"")
        );

        assertTrue(
                json.contains("\"fabrics\"")
        );
    }

    @Test
    void testApiWithoutRequirements()
            throws IOException, InterruptedException {

        HttpResponse<String> response = callApi(
                "sơ mi",
                "200000",
                "ít nhăn",
                ""
        );

        assertEquals(200, response.statusCode());

        assertTrue(
                response.body().contains(
                        "\"combined_preference\":\"ít nhăn\""
                )
        );

        assertTrue(
                response.body().contains("\"fabrics\"")
        );
    }

    @Test
    void testRejectNegativeBudget()
            throws IOException, InterruptedException {

        HttpResponse<String> response = callApi(
                "sơ mi",
                "-1000",
                "mềm",
                ""
        );

        assertEquals(400, response.statusCode());
    }

    @Test
    void testRejectBlankGarment()
            throws IOException, InterruptedException {

        HttpResponse<String> response = callApi(
                "",
                "200000",
                "mềm",
                ""
        );

        assertEquals(400, response.statusCode());
    }

    @Test
    void testMissingBudget()
            throws IOException, InterruptedException {

        String url = "http://localhost:" + port
                + "/api/recommendations"
                + "?garment=" + encode("sơ mi")
                + "&preference=" + encode("mềm");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8
                )
        );

        assertEquals(400, response.statusCode());
    }

    @Test
    void testNonNumericBudget()
            throws IOException, InterruptedException {

        HttpResponse<String> response = callApi(
                "sơ mi",
                "abc",
                "mềm",
                ""
        );

        assertEquals(400, response.statusCode());
    }

    @Test
    void testMissingGarment()
            throws IOException, InterruptedException {

        String url = "http://localhost:" + port
                + "/api/recommendations"
                + "?budget=200000"
                + "&preference=" + encode("mềm");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8
                )
        );

        assertEquals(400, response.statusCode());
    }

    @Test
    void testRejectTooLongRequirements()
            throws IOException, InterruptedException {

        String longRequirements = "a".repeat(1001);

        HttpResponse<String> response = callApi(
                "sơ mi",
                "200000",
                "mềm",
                longRequirements
        );

        assertEquals(
                400,
                response.statusCode(),
                "API phai tu choi yeu cau bo sung qua 1000 ky tu"
        );
    }

    @Test
    void testMissingBudget()
            throws IOException, InterruptedException {

        String url = "http://localhost:" + port
                + "/api/recommendations"
                + "?garment=" + encode("sơ mi")
                + "&preference=" + encode("mềm");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8
                )
        );

        assertEquals(400, response.statusCode());
    }

    @Test
    void testNonNumericBudget()
            throws IOException, InterruptedException {

        HttpResponse<String> response = callApi(
                "sơ mi",
                "abc",
                "mềm",
                ""
        );

        assertEquals(400, response.statusCode());
    }

    @Test
    void testMissingGarment()
            throws IOException, InterruptedException {

        String url = "http://localhost:" + port
                + "/api/recommendations"
                + "?budget=200000"
                + "&preference=" + encode("mềm");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8
                )
        );

        assertEquals(400, response.statusCode());
    }

    @Test
    void testRejectTooLongRequirements()
            throws IOException, InterruptedException {

        String longRequirements = "a".repeat(1001);

        HttpResponse<String> response = callApi(
                "sơ mi",
                "200000",
                "mềm",
                longRequirements
        );

        assertEquals(
                400,
                response.statusCode(),
                "API phai tu choi yeu cau bo sung qua 1000 ky tu"
        );
    }

}
