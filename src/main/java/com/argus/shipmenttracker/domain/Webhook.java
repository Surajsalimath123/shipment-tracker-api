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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@Entity
@Table(name = "webhooks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Webhook {

    @Id
    @Column(name = "webhook_id", nullable = false, updatable = false)
    private UUID webhookId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "event_types", nullable = false, columnDefinition = "text[]")
    private String[] eventTypes;

    @Column(name = "secret", nullable = false)
    private String secret;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "description")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @PrePersist
    void prePersist() {
        if (webhookId == null) {
            webhookId = UUID.randomUUID();
        }
    }

    public boolean acceptsEventType(EventType type) {
        if (eventTypes == null || eventTypes.length == 0) {
            return false;
        }
        return Arrays.stream(eventTypes)
            .anyMatch(t -> "*".equals(t) || type.name().equals(t));
    }
}
