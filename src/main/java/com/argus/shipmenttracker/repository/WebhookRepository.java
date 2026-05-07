package com.argus.shipmenttracker.repository;

import com.argus.shipmenttracker.domain.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookRepository extends JpaRepository<Webhook, UUID> {

    Optional<Webhook> findByWebhookIdAndCompanyId(UUID webhookId, UUID companyId);

    List<Webhook> findAllByCompanyIdAndActiveTrue(UUID companyId);
}
