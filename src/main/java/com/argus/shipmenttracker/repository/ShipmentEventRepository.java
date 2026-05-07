package com.argus.shipmenttracker.repository;

import com.argus.shipmenttracker.domain.ShipmentEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShipmentEventRepository extends JpaRepository<ShipmentEvent, UUID> {

    /**
     * Paginated event history for a single shipment, scoped by tenant.
     * Uses the composite index {@code idx_events_company_shipment_time}.
     */
    Page<ShipmentEvent> findByCompanyIdAndShipmentIdOrderByOccurredAtDesc(
        UUID companyId,
        String shipmentId,
        Pageable pageable);
}
