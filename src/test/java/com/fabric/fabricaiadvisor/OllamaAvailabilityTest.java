package com.fabric.fabricaiadvisor;

import com.fabric.fabricaiadvisor.service.OllamaService;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OllamaAvailabilityTest {
    @Test void acceptsInstalledModel() {
        var builder = RestClient.builder().baseUrl("http://model.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://model.test/api/tags")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"models\":[{\"name\":\"gemma3:4b\"}]}", MediaType.APPLICATION_JSON));
        assertDoesNotThrow(() -> new OllamaService(builder.build(), "gemma3:4b").checkAvailability());
        server.verify();
    }
    @Test void resolvesDefaultLatestTag() {
        var builder = RestClient.builder().baseUrl("http://model.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://model.test/api/tags"))
                .andRespond(withSuccess("{\"models\":[{\"name\":\"small:latest\"}]}", MediaType.APPLICATION_JSON));
        assertDoesNotThrow(() -> new OllamaService(builder.build(), "small").checkAvailability());
        server.verify();
    }
    @Test void rejectsMissingModel() {
        var builder = RestClient.builder().baseUrl("http://model.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://model.test/api/tags"))
                .andRespond(withSuccess("{\"models\":[]}", MediaType.APPLICATION_JSON));
        assertThrows(IllegalStateException.class, () -> new OllamaService(builder.build(), "missing").checkAvailability());
        server.verify();
    }
    @Test void reportsUnavailableEndpoint() {
        var builder = RestClient.builder().baseUrl("http://model.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://model.test/api/tags")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(RestClientException.class, () -> new OllamaService(builder.build(), "gemma3:4b").checkAvailability());
        server.verify();
    }
}
