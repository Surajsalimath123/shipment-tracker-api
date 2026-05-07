package com.argus.shipmenttracker.dto;

import com.argus.shipmenttracker.domain.Shipment;
import com.argus.shipmenttracker.domain.ShipmentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShipmentStatusResponse(
    String shipmentId,
    ShipmentStatus currentStatus,
    Instant lastEventAt,
    LocationDto lastLocation,
    Instant estimatedEta,
    String origin,
    String destination,
    String carrier
) {
    public static ShipmentStatusResponse from(Shipment shipment) {
        return new ShipmentStatusResponse(
            shipment.getShipmentId(),
            shipment.getCurrentStatus(),
            shipment.getLastEventAt(),
            LocationDto.fromMap(shipment.getLastLocation()),
            shipment.getEstimatedEta(),
            shipment.getOrigin(),
            shipment.getDestination(),
            shipment.getCarrier()
        );
    }
}
