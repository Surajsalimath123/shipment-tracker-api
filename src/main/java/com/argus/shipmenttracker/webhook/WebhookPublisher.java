package com.argus.shipmenttracker.webhook;

import com.argus.shipmenttracker.domain.ShipmentEvent;

/**
 * Strategy interface for dispatching shipment events to webhook subscribers.
 * The default implementation is wired in Phase 6 (async dispatcher with
 * retry and HMAC signing); other implementations could publish to a queue.
 */
public interface WebhookPublisher {

    /**
     * Notify all active webhook subscribers for the event's tenant.
     * The implementation should not block the caller — webhooks are
     * delivered asynchronously after the database transaction commits.
     */
    void publish(ShipmentEvent event);
}
