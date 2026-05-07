package com.argus.shipmenttracker.controller;

import com.argus.shipmenttracker.domain.Shipment;
import com.argus.shipmenttracker.domain.ShipmentEvent;
import com.argus.shipmenttracker.dto.CreateEventRequest;
import com.argus.shipmenttracker.dto.EventResponse;
import com.argus.shipmenttracker.dto.PageResponse;
import com.argus.shipmenttracker.dto.ShipmentStatusResponse;
import com.argus.shipmenttracker.service.ShipmentEventService;
import com.argus.shipmenttracker.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
@Tag(name = "Shipments", description = "Record events and read shipment status")
public class ShipmentController {

    private final ShipmentEventService eventService;
    private final ShipmentService shipmentService;

    @PostMapping("/{shipmentId}/events")
    @Operation(summary = "Record a new event for a shipment",
        description = "Validates and persists the event, updates the shipment's denormalized status, and asynchronously fans out to webhook subscribers.")
    public ResponseEntity<EventResponse> createEvent(
            @Parameter(description = "Shipment identifier (e.g. SHP-12345)") @PathVariable String shipmentId,
            @Valid @RequestBody CreateEventRequest request) {
        ShipmentEvent event = eventService.recordEvent(shipmentId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(EventResponse.from(event));
    }

    @GetMapping("/{shipmentId}/events")
    @Operation(summary = "List events for a shipment, paginated, newest first")
    public PageResponse<EventResponse> listEvents(
            @PathVariable String shipmentId,
            @PageableDefault(size = 50) Pageable pageable) {
        Page<ShipmentEvent> page = eventService.getEvents(shipmentId, pageable);
        return PageResponse.from(page, EventResponse::from);
    }

    @GetMapping("/{shipmentId}/status")
    @Operation(summary = "Current status snapshot (latest location, ETA, condition)")
    public ShipmentStatusResponse getStatus(@PathVariable String shipmentId) {
        Shipment shipment = shipmentService.getCurrentStatus(shipmentId);
        return ShipmentStatusResponse.from(shipment);
    }
}
