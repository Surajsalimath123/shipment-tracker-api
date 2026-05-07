package com.argus.shipmenttracker.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for {@link Shipment}: (companyId, shipmentId).
 * Required by JPA when an entity has multiple {@code @Id} fields.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ShipmentId implements Serializable {

    private UUID companyId;
    private String shipmentId;
}
