package com.argus.shipmenttracker.webhook;

import com.argus.shipmenttracker.domain.ShipmentEvent;
import com.argus.shipmenttracker.domain.Webhook;
import com.argus.shipmenttracker.repository.WebhookRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * Asynchronous webhook dispatcher. Runs on the {@code webhookExecutor}
 * thread pool — the API request returns 201 immediately and dispatch
 * happens on a dedicated thread.
 *
 * Per attempt:
 *   - serialize {@link WebhookEventPayload} as JSON
 *   - sign the body with the webhook's secret (HMAC-SHA256)
 *   - POST it to the subscriber URL with a configurable timeout
 *   - log every attempt to webhook_delivery_logs
 *   - on failure, sleep with exponential backoff and try again
 */
@Slf4j
@Component("asyncWebhookDispatcher")
@Primary
@RequiredArgsConstructor
public class AsyncWebhookDispatcher implements WebhookPublisher {

    private final WebhookRepository webhookRepository;
    private final WebhookDeliveryLogService deliveryLogService;
    private final WebhookSigner signer;
    private final WebhookProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.builder().build();

    @Override
    @Async("webhookExecutor")
    @Transactional(readOnly = true)
    public void publish(ShipmentEvent event) {
        List<Webhook> subscribers = webhookRepository
            .findAllByCompanyIdAndActiveTrue(event.getCompanyId());

        if (subscribers.isEmpty()) {
            log.debug("No active webhooks for company {}; skipping dispatch", event.getCompanyId());
            return;
        }

        String body;
        try {
            body = objectMapper.writeValueAsString(WebhookEventPayload.from(event));
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize webhook payload for event {}", event.getEventId(), ex);
            return;
        }

        for (Webhook webhook : subscribers) {
            if (!webhook.acceptsEventType(event.getEventType())) {
                log.debug("Webhook {} does not subscribe to {}; skipping",
                    webhook.getWebhookId(), event.getEventType());
                continue;
            }
            deliverWithRetry(webhook, event, body);
        }
    }

    private void deliverWithRetry(Webhook webhook, ShipmentEvent event, String body) {
        long backoff = properties.getBackoffInitialMs();

        for (int attempt = 1; attempt <= properties.getMaxRetries(); attempt++) {
            DeliveryAttempt result = sendOnce(webhook, body);

            deliveryLogService.recordAttempt(
                webhook, event, attempt,
                result.statusCode(), result.responseBody(),
                result.errorMessage(), result.durationMs(),
                result.success());

            if (result.success()) {
                deliveryLogService.markSuccess(webhook);
                log.info("Webhook delivered: webhook={} event={} attempt={} status={}",
                    webhook.getWebhookId(), event.getEventId(), attempt, result.statusCode());
                return;
            }

            log.warn("Webhook attempt failed: webhook={} event={} attempt={}/{} status={} error={}",
                webhook.getWebhookId(), event.getEventId(), attempt,
                properties.getMaxRetries(), result.statusCode(), result.errorMessage());

            if (attempt == properties.getMaxRetries()) {
                deliveryLogService.markFailure(webhook);
                log.error("Webhook gave up after {} attempts: webhook={} event={}",
                    properties.getMaxRetries(), webhook.getWebhookId(), event.getEventId());
                return;
            }

            try {
                Thread.sleep(Math.min(backoff, properties.getBackoffMaxMs()));
                backoff = (long) (backoff * properties.getBackoffMultiplier());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private DeliveryAttempt sendOnce(Webhook webhook, String body) {
        long startedAt = System.currentTimeMillis();
        String signature = signer.sign(body, webhook.getSecret());

        try {
            String responseBody = restClient.post()
                .uri(webhook.getUrl())
                .header("Content-Type", "application/json")
                .header(WebhookSigner.SIGNATURE_HEADER, signature)
                .header(WebhookSigner.TIMESTAMP_HEADER, String.valueOf(System.currentTimeMillis()))
                .body(body)
                .retrieve()
                .body(String.class);
            int durationMs = (int) (System.currentTimeMillis() - startedAt);
            return new DeliveryAttempt(true, 200, responseBody, null, durationMs);
        } catch (RestClientResponseException ex) {
            int durationMs = (int) (System.currentTimeMillis() - startedAt);
            return new DeliveryAttempt(
                false,
                ex.getStatusCode().value(),
                ex.getResponseBodyAsString(),
                ex.getMessage(),
                durationMs);
        } catch (Exception ex) {
            int durationMs = (int) (System.currentTimeMillis() - startedAt);
            return new DeliveryAttempt(false, null, null, ex.getMessage(), durationMs);
        }
    }

    private record DeliveryAttempt(
        boolean success,
        Integer statusCode,
        String responseBody,
        String errorMessage,
        int durationMs
    ) {}
}
