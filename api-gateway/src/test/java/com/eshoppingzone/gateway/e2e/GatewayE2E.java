package com.eshoppingzone.gateway.e2e;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayE2E {

    @Test
    void gatewayHealthEndpointIsReachable() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("e2e.run"),
                "Set -De2e.run=true to run against a deployed gateway");

        String baseUrl = System.getenv().getOrDefault("E2E_BASE_URL", "http://localhost:8080")
                .replaceAll("/$", "");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/actuator/health"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("UP"), "Gateway health response should be UP");
    }
}
