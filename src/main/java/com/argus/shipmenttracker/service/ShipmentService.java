package com.argus.shipmenttracker.service;

import com.argus.shipmenttracker.domain.Shipment;
import com.argus.shipmenttracker.exception.ShipmentNotFoundException;
import com.argus.shipmenttracker.repository.ShipmentRepository;
import com.argus.shipmenttracker.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;

    @Transactional(readOnly = true)
    public Shipment getCurrentStatus(String shipmentId) {
        UUID companyId = TenantContext.requireCompanyId();
        return shipmentRepository
            .findByCompanyIdAndShipmentId(companyId, shipmentId)
            .orElseThrow(() -> new ShipmentNotFoundException(shipmentId));
    }
}
