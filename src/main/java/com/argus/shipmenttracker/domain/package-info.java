/**
 * JPA entity classes that map to the partitioned PostgreSQL schema defined
 * in {@code db/migration}. Entities are immutable where the schema is
 * append-only (e.g. {@code ShipmentEvent}).
 */
package com.argus.shipmenttracker.domain;
