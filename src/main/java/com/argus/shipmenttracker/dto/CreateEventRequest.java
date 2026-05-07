package com.argus.shipmenttracker.dto;

import com.argus.shipmenttracker.domain.EventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public record CreateEventRequest(
    @NotNull(message = "eventType is required")
    EventType eventType,

    @NotNull(message = "timestamp is required")
    Instant timestamp,

    @Valid
    LocationDto location,

    Map<String, Object> metadata
) {}
