package com.argus.shipmenttracker.service;

import com.argus.shipmenttracker.domain.EventType;
import com.argus.shipmenttracker.domain.Webhook;
import com.argus.shipmenttracker.dto.CreateWebhookRequest;
import com.argus.shipmenttracker.dto.WebhookResponse;
import com.argus.shipmenttracker.exception.WebhookNotFoundException;
import com.argus.shipmenttracker.repository.WebhookRepository;
import com.argus.shipmenttracker.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookRepository webhookRepository;

    @Transactional
    public WebhookResponse register(CreateWebhookRequest request) {
        UUID companyId = TenantContext.requireCompanyId();
        validateEventTypes(request.eventTypesOrAll());

        String plainSecret = generateSecret();

        Webhook webhook = Webhook.builder()
            .companyId(companyId)
            .url(request.url())
            .eventTypes(request.eventTypesOrAll().toArray(String[]::new))
            .secret(plainSecret)
            .description(request.description())
            .active(true)
            .consecutiveFailures(0)
            .build();

        Webhook saved = webhookRepository.save(webhook);
        log.info("Registered webhook {} for company {}", saved.getWebhookId(), companyId);
        return WebhookResponse.withSecret(saved, plainSecret);
    }

    @Transactional
    public void unregister(UUID webhookId) {
        UUID companyId = TenantContext.requireCompanyId();
        Webhook webhook = webhookRepository.findByWebhookIdAndCompanyId(webhookId, companyId)
            .orElseThrow(() -> new WebhookNotFoundException(webhookId));
        webhookRepository.delete(webhook);
        log.info("Deleted webhook {} for company {}", webhookId, companyId);
    }

    private void validateEventTypes(List<String> types) {
        for (String t : types) {
            if ("*".equals(t)) continue;
            try {
                EventType.valueOf(t);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                    "Unknown event type: " + t + ". Valid values: " + java.util.Arrays.toString(EventType.values()) + " or *");
            }
        }
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
