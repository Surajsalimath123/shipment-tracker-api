package com.argus.shipmenttracker.integration;

import com.argus.shipmenttracker.TestcontainersConfiguration;
import com.argus.shipmenttracker.dto.CreateEventRequest;
import com.argus.shipmenttracker.dto.CreateWebhookRequest;
import com.argus.shipmenttracker.dto.ErrorResponse;
import com.argus.shipmenttracker.dto.EventResponse;
import com.argus.shipmenttracker.dto.LocationDto;
import com.argus.shipmenttracker.dto.ShipmentStatusResponse;
import com.argus.shipmenttracker.dto.TokenRequest;
import com.argus.shipmenttracker.dto.TokenResponse;
import com.argus.shipmenttracker.dto.WebhookResponse;
import com.argus.shipmenttracker.domain.EventType;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ShipmentApiIntegrationTest {

    static final UUID ACME_ID         = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID FASTFREIGHT_ID  = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final String ACME_KEY      = "demo-key-acme-12345";
    static final String FASTFREIGHT_KEY = "demo-key-fast-67890";

    @LocalServerPort int port;
    @Autowired TestRestTemplate http;

    String baseUrl()             { return "http://localhost:" + port; }
    String url(String path)      { return baseUrl() + path; }

    HttpHeaders bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    String tokenFor(UUID companyId, String apiKey) {
        ResponseEntity<TokenResponse> response = http.postForEntity(
            url("/api/v1/auth/token"),
            new TokenRequest(companyId, apiKey),
            TokenResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().accessToken();
    }

    @Test
    @DisplayName("auth: valid creds return a JWT")
    void authIssuesToken() {
        ResponseEntity<TokenResponse> response = http.postForEntity(
            url("/api/v1/auth/token"),
            new TokenRequest(ACME_ID, ACME_KEY),
            TokenResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().tokenType()).isEqualTo("Bearer");
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().companyId()).isEqualTo(ACME_ID);
    }

    @Test
    @DisplayName("auth: wrong api key returns 401")
    void authRejectsBadKey() {
        ResponseEntity<ErrorResponse> response = http.postForEntity(
            url("/api/v1/auth/token"),
            new TokenRequest(ACME_ID, "wrong-key"),
            ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().status()).isEqualTo(401);
        assertThat(response.getBody().title()).isEqualTo("Invalid credentials");
    }

    @Test
    @DisplayName("protected endpoint without token returns 401")
    void unauthenticatedRequest() {
        ResponseEntity<ErrorResponse> response = http.getForEntity(
            url("/api/v1/shipments/SHP-DEMO-001/status"),
            ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("event lifecycle: create -> status updates -> events list shows it")
    void eventLifecycle() {
        String token = tokenFor(ACME_ID, ACME_KEY);

        CreateEventRequest event = new CreateEventRequest(
            EventType.IN_TRANSIT,
            Instant.parse("2026-05-07T20:00:00Z"),
            new LocationDto(40.7128, -74.0060, "New York, NY"),
            Map.of("carrier", "Acme Trucking", "vehicle", "TRUCK-42"));

        ResponseEntity<EventResponse> created = http.exchange(
            url("/api/v1/shipments/SHP-DEMO-001/events"),
            HttpMethod.POST,
            new HttpEntity<>(event, bearer(token)),
            EventResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().eventType()).isEqualTo(EventType.IN_TRANSIT);
        assertThat(created.getBody().eventId()).startsWith("EVT-");

        ResponseEntity<ShipmentStatusResponse> status = http.exchange(
            url("/api/v1/shipments/SHP-DEMO-001/status"),
            HttpMethod.GET,
            new HttpEntity<>(bearer(token)),
            ShipmentStatusResponse.class);

        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status.getBody().currentStatus().name()).isEqualTo("IN_TRANSIT");
        assertThat(status.getBody().lastEventAt()).isEqualTo(event.timestamp());

        ResponseEntity<JsonNode> events = http.exchange(
            url("/api/v1/shipments/SHP-DEMO-001/events?page=0&size=10"),
            HttpMethod.GET,
            new HttpEntity<>(bearer(token)),
            JsonNode.class);

        assertThat(events.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(events.getBody().get("totalElements").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(events.getBody().get("content").isArray()).isTrue();
    }

    @Test
    @DisplayName("multi-tenancy: Acme cannot read FastFreight's shipment")
    void multiTenantIsolation() {
        String acmeToken = tokenFor(ACME_ID, ACME_KEY);

        ResponseEntity<ErrorResponse> response = http.exchange(
            url("/api/v1/shipments/SHP-FF-1001/status"),
            HttpMethod.GET,
            new HttpEntity<>(bearer(acmeToken)),
            ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().title()).isEqualTo("Shipment not found");
    }

    @Test
    @DisplayName("multi-tenancy: FastFreight cannot post events to Acme's shipment")
    void multiTenantIsolationOnWrite() {
        String fastfreightToken = tokenFor(FASTFREIGHT_ID, FASTFREIGHT_KEY);

        CreateEventRequest event = new CreateEventRequest(
            EventType.PICKUP, Instant.now(), null, null);

        ResponseEntity<ErrorResponse> response = http.exchange(
            url("/api/v1/shipments/SHP-DEMO-001/events"),
            HttpMethod.POST,
            new HttpEntity<>(event, bearer(fastfreightToken)),
            ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("validation: invalid latitude returns 400 with field-level errors")
    void validationFailsBadLatitude() {
        String token = tokenFor(ACME_ID, ACME_KEY);

        CreateEventRequest event = new CreateEventRequest(
            EventType.PICKUP,
            Instant.now(),
            new LocationDto(999.0, -74.0, "Invalid"),
            null);

        ResponseEntity<ErrorResponse> response = http.exchange(
            url("/api/v1/shipments/SHP-DEMO-001/events"),
            HttpMethod.POST,
            new HttpEntity<>(event, bearer(token)),
            ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errors()).isNotEmpty();
    }

    @Test
    @DisplayName("webhook: register returns secret once, then delete works")
    void webhookCrud() {
        String token = tokenFor(ACME_ID, ACME_KEY);

        ResponseEntity<WebhookResponse> created = http.exchange(
            url("/api/v1/webhooks"),
            HttpMethod.POST,
            new HttpEntity<>(new CreateWebhookRequest(
                "https://example.com/hook",
                List.of("DELIVERED"),
                "test"),
                bearer(token)),
            WebhookResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().secret()).startsWith("whsec_");
        UUID webhookId = created.getBody().webhookId();

        ResponseEntity<Void> deleted = http.exchange(
            url("/api/v1/webhooks/" + webhookId),
            HttpMethod.DELETE,
            new HttpEntity<>(bearer(token)),
            Void.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("multi-tenancy: tenant A cannot delete tenant B's webhook")
    void webhookCrossTenantDeleteBlocked() {
        String acmeToken = tokenFor(ACME_ID, ACME_KEY);
        String fastfreightToken = tokenFor(FASTFREIGHT_ID, FASTFREIGHT_KEY);

        ResponseEntity<WebhookResponse> created = http.exchange(
            url("/api/v1/webhooks"),
            HttpMethod.POST,
            new HttpEntity<>(new CreateWebhookRequest(
                "https://example.com/acme-hook", List.of("*"), "acme"),
                bearer(acmeToken)),
            WebhookResponse.class);
        UUID acmeWebhookId = created.getBody().webhookId();

        ResponseEntity<ErrorResponse> response = http.exchange(
            url("/api/v1/webhooks/" + acmeWebhookId),
            HttpMethod.DELETE,
            new HttpEntity<>(bearer(fastfreightToken)),
            ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
