package com.argus.shipmenttracker.service;

import com.argus.shipmenttracker.domain.Shipment;
import com.argus.shipmenttracker.domain.ShipmentEvent;
import com.argus.shipmenttracker.dto.CreateEventRequest;
import com.argus.shipmenttracker.exception.ShipmentNotFoundException;
import com.argus.shipmenttracker.repository.ShipmentEventRepository;
import com.argus.shipmenttracker.repository.ShipmentRepository;
import com.argus.shipmenttracker.security.TenantContext;
import com.argus.shipmenttracker.webhook.WebhookPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentEventService {

    private final ShipmentEventRepository eventRepository;
    private final ShipmentRepository shipmentRepository;
    private final WebhookPublisher webhookPublisher;
    private final ApplicationEventPublisher events;

    @Transactional
    public ShipmentEvent recordEvent(String shipmentId, CreateEventRequest request) {
        UUID companyId = TenantContext.requireCompanyId();

        Shipment shipment = shipmentRepository
            .findByCompanyIdAndShipmentId(companyId, shipmentId)
            .orElseThrow(() -> new ShipmentNotFoundException(shipmentId));

        ShipmentEvent event = ShipmentEvent.builder()
            .shipmentId(shipmentId)
            .companyId(companyId)
            .eventType(request.eventType())
            .occurredAt(request.timestamp())
            .location(request.location() != null ? request.location().toMap() : null)
            .metadata(request.metadata())
            .build();

        ShipmentEvent saved = eventRepository.save(event);

        // Update denormalized current_status / last_event_at on the shipment
        shipment.setCurrentStatus(request.eventType().toShipmentStatus());
        shipment.setLastEventAt(request.timestamp());
        if (request.location() != null) {
            shipment.setLastLocation(request.location().toMap());
        }
        shipmentRepository.save(shipment);

        // Defer webhook dispatch until after commit so we never notify
        // subscribers about an event that rolled back.
        events.publishEvent(new EventRecorded(saved));

        log.info("Recorded {} event for shipment={} company={}",
            request.eventType(), shipmentId, companyId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<ShipmentEvent> getEvents(String shipmentId, Pageable pageable) {
        UUID companyId = TenantContext.requireCompanyId();
        if (!shipmentRepository.existsByCompanyIdAndShipmentId(companyId, shipmentId)) {
            throw new ShipmentNotFoundException(shipmentId);
        }
        return eventRepository.findByCompanyIdAndShipmentIdOrderByOccurredAtDesc(
            companyId, shipmentId, pageable);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onEventRecorded(EventRecorded event) {
        webhookPublisher.publish(event.event());
    }

    public record EventRecorded(ShipmentEvent event) {}
}
