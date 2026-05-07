package com.argus.shipmenttracker.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class EventTypeTest {

    @ParameterizedTest
    @CsvSource({
        "PICKUP, PICKUP",
        "IN_TRANSIT, IN_TRANSIT",
        "OUT_FOR_DELIVERY, OUT_FOR_DELIVERY",
        "DELIVERED, DELIVERED",
        "EXCEPTION, EXCEPTION",
        "RETURNED, RETURNED"
    })
    @DisplayName("EventType maps correctly to ShipmentStatus")
    void toShipmentStatus(EventType input, ShipmentStatus expected) {
        assertThat(input.toShipmentStatus()).isEqualTo(expected);
    }
}
