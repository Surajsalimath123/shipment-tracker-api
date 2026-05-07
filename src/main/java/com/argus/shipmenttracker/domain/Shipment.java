package com.argus.shipmenttracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "shipments")
@IdClass(ShipmentId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shipment {

    @Id
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Id
    @Column(name = "shipment_id", nullable = false, length = 64)
    private String shipmentId;

    @Column(name = "origin")
    private String origin;

    @Column(name = "destination")
    private String destination;

    @Column(name = "carrier")
    private String carrier;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false)
    private ShipmentStatus currentStatus;

    @Column(name = "last_event_at")
    private Instant lastEventAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_location", columnDefinition = "jsonb")
    private Map<String, Object> lastLocation;

    @Column(name = "estimated_eta")
    private Instant estimatedEta;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
