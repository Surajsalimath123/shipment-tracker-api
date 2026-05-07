package com.argus.shipmenttracker.controller;

import com.argus.shipmenttracker.dto.CreateWebhookRequest;
import com.argus.shipmenttracker.dto.WebhookResponse;
import com.argus.shipmenttracker.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Subscribe and unsubscribe to event notifications")
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping
    @Operation(summary = "Register a webhook subscription",
        description = "Returns the webhook id and the signing secret. The secret is shown once at creation and cannot be retrieved later — store it now to verify webhook signatures.")
    public ResponseEntity<WebhookResponse> register(@Valid @RequestBody CreateWebhookRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(webhookService.register(request));
    }

    @DeleteMapping("/{webhookId}")
    @Operation(summary = "Unregister a webhook subscription")
    public ResponseEntity<Void> unregister(@PathVariable UUID webhookId) {
        webhookService.unregister(webhookId);
        return ResponseEntity.noContent().build();
    }
}
