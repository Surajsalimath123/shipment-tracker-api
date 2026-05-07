package com.argus.shipmenttracker.dto;

import com.argus.shipmenttracker.domain.Webhook;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebhookResponse(
    UUID webhookId,
    String url,
    List<String> eventTypes,
    String description,
    boolean active,
    String secret,
    Instant createdAt
) {
    /**
     * Response for the create endpoint — includes the secret exactly once.
     * After creation, the secret is never returned again.
     */
    public static WebhookResponse withSecret(Webhook webhook, String plainSecret) {
        return new WebhookResponse(
            webhook.getWebhookId(),
            webhook.getUrl(),
            Arrays.asList(webhook.getEventTypes()),
            webhook.getDescription(),
            webhook.isActive(),
            plainSecret,
            webhook.getCreatedAt()
        );
    }

    public static WebhookResponse from(Webhook webhook) {
        return new WebhookResponse(
            webhook.getWebhookId(),
            webhook.getUrl(),
            Arrays.asList(webhook.getEventTypes()),
            webhook.getDescription(),
            webhook.isActive(),
            null,
            webhook.getCreatedAt()
        );
    }
}
