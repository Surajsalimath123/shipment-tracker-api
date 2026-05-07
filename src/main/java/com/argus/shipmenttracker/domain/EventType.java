package com.argus.shipmenttracker.domain;

public enum EventType {
    PICKUP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    EXCEPTION,
    RETURNED;

    public ShipmentStatus toShipmentStatus() {
        return switch (this) {
            case PICKUP            -> ShipmentStatus.PICKUP;
            case IN_TRANSIT        -> ShipmentStatus.IN_TRANSIT;
            case OUT_FOR_DELIVERY  -> ShipmentStatus.OUT_FOR_DELIVERY;
            case DELIVERED         -> ShipmentStatus.DELIVERED;
            case EXCEPTION         -> ShipmentStatus.EXCEPTION;
            case RETURNED          -> ShipmentStatus.RETURNED;
        };
    }
}
