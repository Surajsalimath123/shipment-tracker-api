package com.argus.shipmenttracker.repository;

import com.argus.shipmenttracker.domain.WebhookDeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLog, UUID> {
}
