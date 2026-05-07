package com.argus.shipmenttracker.webhook;

import com.argus.shipmenttracker.domain.EventType;
import com.argus.shipmenttracker.domain.ShipmentEvent;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * The exact JSON shape we POST to webhook subscribers when a shipment
 * event is recorded. Documented as part of the public API contract so
 * customers can build verifiers.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebhookEventPayload(
    String eventId,
    String shipmentId,
    String companyId,
    EventType eventType,
    Instant timestamp,
    Map<String, Object> location,
    Map<String, Object> metadata
) {
    public static WebhookEventPayload from(ShipmentEvent event) {
        return new WebhookEventPayload(
            event.getEventId().toString(),
            event.getShipmentId(),
            event.getCompanyId().toString(),
            event.getEventType(),
            event.getOccurredAt(),
            event.getLocation(),
            event.getMetadata()
        );
    }
}
