package com.argus.shipmenttracker.exception;

public class ShipmentNotFoundException extends RuntimeException {
    public ShipmentNotFoundException(String shipmentId) {
        super("Shipment not found: " + shipmentId);
    }
}
