package com.argus.shipmenttracker.domain;

public enum ShipmentStatus {
    CREATED,
    PICKUP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    EXCEPTION,
    RETURNED,
    CANCELLED
}
