package com.argus.shipmenttracker.webhook;

import com.argus.shipmenttracker.domain.ShipmentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Fallback publisher used during early development before the real
 * dispatcher exists. Replaced in Phase 6 by AsyncWebhookDispatcher.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(name = "asyncWebhookDispatcher")
public class NoOpWebhookPublisher implements WebhookPublisher {

    @Override
    public void publish(ShipmentEvent event) {
        log.debug("Webhook publish (noop) for event {}", event.getEventId());
    }
}
