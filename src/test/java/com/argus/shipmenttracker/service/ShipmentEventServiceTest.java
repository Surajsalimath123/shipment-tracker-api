package com.argus.shipmenttracker.service;

import com.argus.shipmenttracker.domain.EventType;
import com.argus.shipmenttracker.domain.Shipment;
import com.argus.shipmenttracker.domain.ShipmentEvent;
import com.argus.shipmenttracker.domain.ShipmentStatus;
import com.argus.shipmenttracker.dto.CreateEventRequest;
import com.argus.shipmenttracker.dto.LocationDto;
import com.argus.shipmenttracker.exception.ShipmentNotFoundException;
import com.argus.shipmenttracker.repository.ShipmentEventRepository;
import com.argus.shipmenttracker.repository.ShipmentRepository;
import com.argus.shipmenttracker.security.TenantContext;
import com.argus.shipmenttracker.webhook.WebhookPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentEventServiceTest {

    @Mock ShipmentEventRepository eventRepository;
    @Mock ShipmentRepository shipmentRepository;
    @Mock WebhookPublisher webhookPublisher;
    @Mock ApplicationEventPublisher events;

    @InjectMocks ShipmentEventService service;

    UUID companyId;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        TenantContext.setCompanyId(companyId);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("recordEvent persists the event and updates shipment status")
    void recordEvent() {
        Shipment shipment = Shipment.builder()
            .shipmentId("SHP-1").companyId(companyId)
            .currentStatus(ShipmentStatus.CREATED)
            .build();
        when(shipmentRepository.findByCompanyIdAndShipmentId(companyId, "SHP-1"))
            .thenReturn(Optional.of(shipment));
        when(eventRepository.save(any(ShipmentEvent.class)))
            .thenAnswer(inv -> {
                ShipmentEvent e = inv.getArgument(0);
                e.setEventId(UUID.randomUUID());
                return e;
            });

        CreateEventRequest req = new CreateEventRequest(
            EventType.IN_TRANSIT,
            Instant.parse("2026-05-07T10:00:00Z"),
            new LocationDto(40.7, -74.0, "New York"),
            null);

        ShipmentEvent result = service.recordEvent("SHP-1", req);

        assertThat(result.getEventType()).isEqualTo(EventType.IN_TRANSIT);
        assertThat(result.getCompanyId()).isEqualTo(companyId);

        ArgumentCaptor<Shipment> shipmentCap = ArgumentCaptor.forClass(Shipment.class);
        verify(shipmentRepository).save(shipmentCap.capture());
        assertThat(shipmentCap.getValue().getCurrentStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(shipmentCap.getValue().getLastEventAt()).isEqualTo(req.timestamp());
        assertThat(shipmentCap.getValue().getLastLocation()).containsEntry("address", "New York");

        verify(events).publishEvent(any(ShipmentEventService.EventRecorded.class));
    }

    @Test
    @DisplayName("recordEvent throws when shipment doesn't exist for this tenant")
    void shipmentNotFound() {
        when(shipmentRepository.findByCompanyIdAndShipmentId(companyId, "MISSING"))
            .thenReturn(Optional.empty());

        CreateEventRequest req = new CreateEventRequest(
            EventType.PICKUP, Instant.now(), null, null);

        assertThatThrownBy(() -> service.recordEvent("MISSING", req))
            .isInstanceOf(ShipmentNotFoundException.class);
    }

    @Test
    @DisplayName("getEvents returns page only after verifying shipment exists for tenant")
    void getEventsScopedByTenant() {
        when(shipmentRepository.existsByCompanyIdAndShipmentId(companyId, "SHP-1"))
            .thenReturn(true);
        Page<ShipmentEvent> page = new PageImpl<>(List.of());
        when(eventRepository.findByCompanyIdAndShipmentIdOrderByOccurredAtDesc(
            companyId, "SHP-1", Pageable.unpaged()))
            .thenReturn(page);

        Page<ShipmentEvent> result = service.getEvents("SHP-1", Pageable.unpaged());

        assertThat(result).isSameAs(page);
    }

    @Test
    @DisplayName("getEvents 404s if shipment doesn't belong to tenant")
    void getEventsTenantIsolated() {
        when(shipmentRepository.existsByCompanyIdAndShipmentId(companyId, "OTHER-CO-SHIPMENT"))
            .thenReturn(false);

        assertThatThrownBy(() -> service.getEvents("OTHER-CO-SHIPMENT", Pageable.unpaged()))
            .isInstanceOf(ShipmentNotFoundException.class);
    }
}
