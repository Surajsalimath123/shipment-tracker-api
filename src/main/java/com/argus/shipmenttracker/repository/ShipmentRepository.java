package com.argus.shipmenttracker.repository;

import com.argus.shipmenttracker.domain.Shipment;
import com.argus.shipmenttracker.domain.ShipmentId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, ShipmentId> {

    Optional<Shipment> findByCompanyIdAndShipmentId(UUID companyId, String shipmentId);

    boolean existsByCompanyIdAndShipmentId(UUID companyId, String shipmentId);
}
