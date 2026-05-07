package com.argus.shipmenttracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_delivery_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDeliveryLog {

    @Id
    @Column(name = "delivery_id", nullable = false, updatable = false)
    private UUID deliveryId;

    @Column(name = "webhook_id", nullable = false)
    private UUID webhookId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "attempt_number", nullable = false)
    private short attemptNumber;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "delivered_at", nullable = false, updatable = false)
    private Instant deliveredAt;

    @Column(name = "success", nullable = false)
    private boolean success;

    @PrePersist
    void prePersist() {
        if (deliveryId == null) {
            deliveryId = UUID.randomUUID();
        }
        if (deliveredAt == null) {
            deliveredAt = Instant.now();
        }
    }
}
