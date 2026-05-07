-- =============================================================================
-- V2: Monthly partitions for shipment_events and webhook_delivery_logs
--
-- We pre-create partitions through Dec 2026. In production, a scheduled
-- job (or pg_partman extension) creates the next month's partition before
-- the boundary is crossed. Writes that fall outside the defined range land
-- in the default partition so the API never errors on a missing partition,
-- but operators are alerted to provision more.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- shipment_events partitions
-- ---------------------------------------------------------------------------

CREATE TABLE shipment_events_2026_04 PARTITION OF shipment_events
    FOR VALUES FROM ('2026-04-01 00:00:00+00') TO ('2026-05-01 00:00:00+00');

CREATE TABLE shipment_events_2026_05 PARTITION OF shipment_events
    FOR VALUES FROM ('2026-05-01 00:00:00+00') TO ('2026-06-01 00:00:00+00');

CREATE TABLE shipment_events_2026_06 PARTITION OF shipment_events
    FOR VALUES FROM ('2026-06-01 00:00:00+00') TO ('2026-07-01 00:00:00+00');

CREATE TABLE shipment_events_2026_07 PARTITION OF shipment_events
    FOR VALUES FROM ('2026-07-01 00:00:00+00') TO ('2026-08-01 00:00:00+00');

CREATE TABLE shipment_events_2026_08 PARTITION OF shipment_events
    FOR VALUES FROM ('2026-08-01 00:00:00+00') TO ('2026-09-01 00:00:00+00');

CREATE TABLE shipment_events_2026_09 PARTITION OF shipment_events
    FOR VALUES FROM ('2026-09-01 00:00:00+00') TO ('2026-10-01 00:00:00+00');

CREATE TABLE shipment_events_2026_10 PARTITION OF shipment_events
    FOR VALUES FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00');

CREATE TABLE shipment_events_2026_11 PARTITION OF shipment_events
    FOR VALUES FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00');

CREATE TABLE shipment_events_2026_12 PARTITION OF shipment_events
    FOR VALUES FROM ('2026-12-01 00:00:00+00') TO ('2027-01-01 00:00:00+00');

-- Default partition catches any row outside the explicit ranges.
-- A monitoring alert on row count > 0 here means "provision more partitions".
CREATE TABLE shipment_events_default PARTITION OF shipment_events DEFAULT;

-- ---------------------------------------------------------------------------
-- webhook_delivery_logs partitions
-- ---------------------------------------------------------------------------

CREATE TABLE webhook_delivery_logs_2026_04 PARTITION OF webhook_delivery_logs
    FOR VALUES FROM ('2026-04-01 00:00:00+00') TO ('2026-05-01 00:00:00+00');

CREATE TABLE webhook_delivery_logs_2026_05 PARTITION OF webhook_delivery_logs
    FOR VALUES FROM ('2026-05-01 00:00:00+00') TO ('2026-06-01 00:00:00+00');

CREATE TABLE webhook_delivery_logs_2026_06 PARTITION OF webhook_delivery_logs
    FOR VALUES FROM ('2026-06-01 00:00:00+00') TO ('2026-07-01 00:00:00+00');

CREATE TABLE webhook_delivery_logs_2026_07 PARTITION OF webhook_delivery_logs
    FOR VALUES FROM ('2026-07-01 00:00:00+00') TO ('2026-08-01 00:00:00+00');

CREATE TABLE webhook_delivery_logs_2026_08 PARTITION OF webhook_delivery_logs
    FOR VALUES FROM ('2026-08-01 00:00:00+00') TO ('2026-09-01 00:00:00+00');

CREATE TABLE webhook_delivery_logs_2026_09 PARTITION OF webhook_delivery_logs
    FOR VALUES FROM ('2026-09-01 00:00:00+00') TO ('2026-10-01 00:00:00+00');

CREATE TABLE webhook_delivery_logs_2026_10 PARTITION OF webhook_delivery_logs
    FOR VALUES FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00');

CREATE TABLE webhook_delivery_logs_2026_11 PARTITION OF webhook_delivery_logs
    FOR VALUES FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00');

CREATE TABLE webhook_delivery_logs_2026_12 PARTITION OF webhook_delivery_logs
    FOR VALUES FROM ('2026-12-01 00:00:00+00') TO ('2027-01-01 00:00:00+00');

CREATE TABLE webhook_delivery_logs_default PARTITION OF webhook_delivery_logs DEFAULT;
