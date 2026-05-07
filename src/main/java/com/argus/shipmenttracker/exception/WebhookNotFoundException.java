package com.argus.shipmenttracker.exception;

import java.util.UUID;

public class WebhookNotFoundException extends RuntimeException {
    public WebhookNotFoundException(UUID webhookId) {
        super("Webhook not found: " + webhookId);
    }
}
