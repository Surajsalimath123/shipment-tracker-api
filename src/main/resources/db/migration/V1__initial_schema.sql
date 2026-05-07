-- =============================================================================
-- V1: Initial schema for the Shipment Tracker API
--
-- Design highlights:
--   * Multi-tenancy: every domain table carries company_id.
--   * Append-only events: shipment_events is partitioned by RANGE(occurred_at)
--     so tens of millions of rows per month do not affect query times.
--   * Heavy indexing on the (company_id, shipment_id, occurred_at) tuple
--     because that is the access pattern for the GET /events endpoint.
--   * Webhook delivery logs are also partitioned because the audit volume
--     grows roughly with the event volume.
--   * pgcrypto provides bcrypt-compatible api-key hashing
--     (Spring Security's BCryptPasswordEncoder verifies these hashes).
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =============================================================================
-- companies — the tenant root
-- =============================================================================
CREATE TABLE companies (
    company_id      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    api_key_hash    VARCHAR(72)  NOT NULL UNIQUE,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_companies_active
    ON companies (is_active)
    WHERE is_active = TRUE;

COMMENT ON TABLE  companies            IS 'Tenant root. Every other domain row references company_id.';
COMMENT ON COLUMN companies.api_key_hash IS 'Bcrypt hash (cost 10) of the company API key. Verified by Spring Security at /api/v1/auth/token.';

-- =============================================================================
-- shipments — core shipment record (one row per shipment)
--
-- current_status, last_event_at, last_location are DENORMALIZED from
-- shipment_events for fast /status reads. Maintained by the application
-- when each event is recorded.
-- =============================================================================
CREATE TABLE shipments (
    shipment_id     VARCHAR(64)  NOT NULL,
    company_id      UUID         NOT NULL REFERENCES companies (company_id),
    origin          VARCHAR(255),
    destination     VARCHAR(255),
    carrier         VARCHAR(100),
    current_status  VARCHAR(50)  NOT NULL DEFAULT 'CREATED',
    last_event_at   TIMESTAMPTZ,
    last_location   JSONB,
    estimated_eta   TIMESTAMPTZ,
    metadata        JSONB        DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (company_id, shipment_id),
    CONSTRAINT chk_shipments_status CHECK (current_status IN
        ('CREATED', 'PICKUP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY',
         'DELIVERED', 'EXCEPTION', 'RETURNED', 'CANCELLED'))
);

-- Partial indexes are smaller and faster than full indexes on enum-style columns
CREATE INDEX idx_shipments_company_status
    ON shipments (company_id, current_status);

CREATE INDEX idx_shipments_company_eta
    ON shipments (company_id, estimated_eta)
    WHERE estimated_eta IS NOT NULL;

COMMENT ON TABLE shipments IS
    'One row per shipment. current_status / last_event_at are denormalized for O(1) /status reads.';

-- =============================================================================
-- shipment_events — append-only event log, PARTITIONED BY RANGE(occurred_at)
--
-- Why partitioning:
--   * 10,000 events/minute = ~430 million events/month.
--   * Without partitioning, vacuum, indexes, and queries degrade with table size.
--   * With monthly partitions, each query targets ~430M rows max, and old
--     partitions can be detached (archived to cold storage) in O(1).
--
-- Why composite PK (event_id, occurred_at):
--   * Postgres requires the partition key to be part of every unique constraint.
--   * event_id alone cannot be the PK on a partitioned table.
--   * UUID collisions are negligible so we treat event_id as the logical id.
--
-- Why GIN on metadata:
--   * Carrier-specific metadata is unstructured. GIN allows fast lookups by
--     metadata->>'carrier' or @> '{"vehicle":"TRUCK-789"}' queries.
-- =============================================================================
CREATE TABLE shipment_events (
    event_id      UUID         NOT NULL DEFAULT gen_random_uuid(),
    shipment_id   VARCHAR(64)  NOT NULL,
    company_id    UUID         NOT NULL,
    event_type    VARCHAR(50)  NOT NULL,
    occurred_at   TIMESTAMPTZ  NOT NULL,
    location      JSONB,
    metadata      JSONB,
    received_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, occurred_at),
    FOREIGN KEY (company_id, shipment_id)
        REFERENCES shipments (company_id, shipment_id),
    CONSTRAINT chk_events_type CHECK (event_type IN
        ('PICKUP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY',
         'DELIVERED', 'EXCEPTION', 'RETURNED'))
) PARTITION BY RANGE (occurred_at);

-- Indexes on partitioned tables propagate to all partitions automatically.
CREATE INDEX idx_events_company_shipment_time
    ON shipment_events (company_id, shipment_id, occurred_at DESC);

CREATE INDEX idx_events_company_received
    ON shipment_events (company_id, received_at DESC);

CREATE INDEX idx_events_metadata_gin
    ON shipment_events USING GIN (metadata);

COMMENT ON TABLE shipment_events IS
    'Immutable, append-only event stream. Partitioned by occurred_at month for scale.';

-- =============================================================================
-- webhooks — subscriptions per company
--
-- event_types is a TEXT[] so callers can subscribe to specific events
-- (e.g. only DELIVERED) or use {'*'} for all.
-- secret is used as the HMAC-SHA256 key when signing outbound webhook bodies.
-- =============================================================================
CREATE TABLE webhooks (
    webhook_id      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID          NOT NULL REFERENCES companies (company_id),
    url             VARCHAR(2048) NOT NULL,
    event_types     TEXT[]        NOT NULL DEFAULT ARRAY['*'],
    secret          VARCHAR(255)  NOT NULL,
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    description     VARCHAR(255),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_success_at TIMESTAMPTZ,
    last_failure_at TIMESTAMPTZ,
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_webhooks_url CHECK (url ~ '^https?://')
);

CREATE INDEX idx_webhooks_company_active
    ON webhooks (company_id)
    WHERE is_active = TRUE;

COMMENT ON TABLE webhooks IS
    'Outbound webhook subscriptions. One company can register multiple URLs with different event-type filters.';

-- =============================================================================
-- webhook_delivery_logs — audit trail for every webhook attempt
--
-- Volume scales with event volume × subscribers, so we partition this too.
-- Older partitions can be archived to cold storage after the SLA window.
-- =============================================================================
CREATE TABLE webhook_delivery_logs (
    delivery_id     UUID         NOT NULL DEFAULT gen_random_uuid(),
    webhook_id      UUID         NOT NULL,
    event_id        UUID         NOT NULL,
    company_id      UUID         NOT NULL,
    attempt_number  SMALLINT     NOT NULL,
    response_status INTEGER,
    response_body   TEXT,
    error_message   TEXT,
    duration_ms     INTEGER,
    delivered_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    success         BOOLEAN      NOT NULL,
    PRIMARY KEY (delivery_id, delivered_at)
) PARTITION BY RANGE (delivered_at);

CREATE INDEX idx_delivery_logs_webhook
    ON webhook_delivery_logs (webhook_id, delivered_at DESC);

CREATE INDEX idx_delivery_logs_event
    ON webhook_delivery_logs (event_id);

CREATE INDEX idx_delivery_logs_company_failures
    ON webhook_delivery_logs (company_id, delivered_at DESC)
    WHERE success = FALSE;

COMMENT ON TABLE webhook_delivery_logs IS
    'One row per webhook attempt (including retries). Partitioned by delivered_at month.';

-- =============================================================================
-- api_rate_limits — audit / metric table for rate limiting
--
-- Bucket4j enforces in-memory; this table records traffic for analytics
-- and capacity planning. Updated atomically by an UPSERT.
-- =============================================================================
CREATE TABLE api_rate_limits (
    company_id      UUID         NOT NULL,
    window_started  TIMESTAMPTZ  NOT NULL,
    request_count   INTEGER      NOT NULL DEFAULT 0,
    blocked_count   INTEGER      NOT NULL DEFAULT 0,
    PRIMARY KEY (company_id, window_started)
);

COMMENT ON TABLE api_rate_limits IS
    'Per-minute audit of rate limit usage. Bucket4j is the enforcement layer; this is the metrics layer.';

-- =============================================================================
-- updated_at trigger — keep updated_at fresh on row updates
-- =============================================================================
CREATE OR REPLACE FUNCTION trg_set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER companies_set_updated_at
    BEFORE UPDATE ON companies
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

CREATE TRIGGER shipments_set_updated_at
    BEFORE UPDATE ON shipments
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
