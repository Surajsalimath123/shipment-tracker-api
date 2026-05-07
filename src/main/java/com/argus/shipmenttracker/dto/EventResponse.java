package com.argus.shipmenttracker.dto;

import com.argus.shipmenttracker.domain.EventType;
import com.argus.shipmenttracker.domain.ShipmentEvent;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventResponse(
    String eventId,
    String shipmentId,
    EventType eventType,
    Instant timestamp,
    Instant receivedAt,
    LocationDto location,
    Map<String, Object> metadata
) {
    public static EventResponse from(ShipmentEvent event) {
        return new EventResponse(
            "EVT-" + event.getEventId(),
            event.getShipmentId(),
            event.getEventType(),
            event.getOccurredAt(),
            event.getReceivedAt(),
            LocationDto.fromMap(event.getLocation()),
            event.getMetadata()
        );
    }
}
