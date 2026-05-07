package com.argus.shipmenttracker.webhook;

import com.argus.shipmenttracker.domain.ShipmentEvent;
import com.argus.shipmenttracker.domain.Webhook;
import com.argus.shipmenttracker.domain.WebhookDeliveryLog;
import com.argus.shipmenttracker.repository.WebhookDeliveryLogRepository;
import com.argus.shipmenttracker.repository.WebhookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * All webhook persistence runs in {@code REQUIRES_NEW} transactions so a
 * failed delivery never rolls back unrelated work, and a successful audit
 * write commits even if the surrounding async task throws afterward.
 */
@Service
@RequiredArgsConstructor
public class WebhookDeliveryLogService {

    private final WebhookDeliveryLogRepository deliveryLogRepository;
    private final WebhookRepository webhookRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAttempt(Webhook webhook,
                              ShipmentEvent event,
                              int attemptNumber,
                              Integer responseStatus,
                              String responseBody,
                              String errorMessage,
                              Integer durationMs,
                              boolean success) {
        WebhookDeliveryLog logEntry = WebhookDeliveryLog.builder()
            .webhookId(webhook.getWebhookId())
            .eventId(event.getEventId())
            .companyId(event.getCompanyId())
            .attemptNumber((short) attemptNumber)
            .responseStatus(responseStatus)
            .responseBody(truncate(responseBody, 4_000))
            .errorMessage(truncate(errorMessage, 4_000))
            .durationMs(durationMs)
            .success(success)
            .build();
        deliveryLogRepository.save(logEntry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(Webhook webhook) {
        webhookRepository.findById(webhook.getWebhookId()).ifPresent(w -> {
            w.setLastSuccessAt(Instant.now());
            w.setConsecutiveFailures(0);
            webhookRepository.save(w);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailure(Webhook webhook) {
        webhookRepository.findById(webhook.getWebhookId()).ifPresent(w -> {
            w.setLastFailureAt(Instant.now());
            w.setConsecutiveFailures(w.getConsecutiveFailures() + 1);
            webhookRepository.save(w);
        });
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
