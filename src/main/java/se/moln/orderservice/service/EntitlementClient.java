package se.moln.orderservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class EntitlementClient {
    private final String userServiceBase;
    private final HttpClient http = HttpClient.newHttpClient();

    public EntitlementClient(@Value("${USERSERVICE_URL:http://user-service:8083}") String base) {
        this.userServiceBase = base;
    }

    private Map<String, Object> parseJson(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            return om.readValue(json, Map.class);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }

    private String toJson(Map<String, Object> map) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            return om.writeValueAsString(map);
        } catch (Throwable ignore) {
            return map.toString();
        }
    }

    public boolean hasEntitlement(String authBearerToken, String sku) {
        try {
            URI uri = URI.create(userServiceBase + "/api/users/me/entitlements?sku=" + java.net.URLEncoder.encode(sku, StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, authBearerToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resp.statusCode() == 200;
        } catch (Exception e) {
            throw new RuntimeException("Entitlement check failed", e);
        }
    }

    public boolean consume(String authBearerToken, String sku, int count) {
        try {
            URI uri = URI.create(userServiceBase + "/api/users/me/entitlements/consume");
            String body = toJson(Map.of("sku", sku, "count", count));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, authBearerToken)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) return true;
            if (resp.statusCode() == 409) return false; // insufficient
            throw new RuntimeException("Entitlement consume failed: " + resp.statusCode() + " - " + resp.body());
        } catch (Exception e) {
            throw new RuntimeException("Entitlement consume error", e);
        }
    }

    public void grantEntitlement(String authBearerToken, String sku, int count) {
        try {
            URI uri = URI.create(userServiceBase + "/api/users/me/entitlements/grant");
            String body = toJson(Map.of("sku", sku, "count", count));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, authBearerToken)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                throw new RuntimeException("Grant entitlement failed: " + resp.statusCode() + " - " + resp.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("Grant entitlement error", e);
        }
    }
}
