-- =============================================================================
-- Shipment Tracker API — PostgreSQL schema reference
--
-- This file is a single-place reference for the database schema. It is the
-- consolidation of:
--   * V1__initial_schema.sql        (base tables, indexes, triggers)
--   * V2__create_event_partitions.sql (monthly partitions through 2026)
--
-- The application applies the schema via Flyway migrations under
-- src/main/resources/db/migration/. Use this file for review or to
-- bootstrap a database manually.
--
-- Schema overview:
--
--   companies                 (tenant root)
--       └─ shipments          (composite PK on company_id + shipment_id)
--             └─ shipment_events  (PARTITIONED BY RANGE(occurred_at))
--       └─ webhooks
--             └─ webhook_delivery_logs (PARTITIONED BY RANGE(delivered_at))
--       └─ api_rate_limits    (audit)
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

CREATE INDEX idx_companies_active ON companies (is_active) WHERE is_active = TRUE;

-- =============================================================================
-- shipments — core shipment record
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

CREATE INDEX idx_shipments_company_status ON shipments (company_id, current_status);
CREATE INDEX idx_shipments_company_eta    ON shipments (company_id, estimated_eta)
    WHERE estimated_eta IS NOT NULL;

-- =============================================================================
-- shipment_events — append-only event stream (partitioned monthly)
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

CREATE INDEX idx_events_company_shipment_time
    ON shipment_events (company_id, shipment_id, occurred_at DESC);
CREATE INDEX idx_events_company_received
    ON shipment_events (company_id, received_at DESC);
CREATE INDEX idx_events_metadata_gin
    ON shipment_events USING GIN (metadata);

-- One partition per month (see V2__create_event_partitions.sql for the full set).
CREATE TABLE shipment_events_2026_05 PARTITION OF shipment_events
    FOR VALUES FROM ('2026-05-01 00:00:00+00') TO ('2026-06-01 00:00:00+00');
-- ... additional monthly partitions ...
CREATE TABLE shipment_events_default PARTITION OF shipment_events DEFAULT;

-- =============================================================================
-- webhooks — outbound subscription registry
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

CREATE INDEX idx_webhooks_company_active ON webhooks (company_id) WHERE is_active = TRUE;

-- =============================================================================
-- webhook_delivery_logs — per-attempt audit trail (partitioned monthly)
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
CREATE INDEX idx_delivery_logs_event ON webhook_delivery_logs (event_id);
CREATE INDEX idx_delivery_logs_company_failures
    ON webhook_delivery_logs (company_id, delivered_at DESC)
    WHERE success = FALSE;

-- =============================================================================
-- api_rate_limits — usage audit
-- =============================================================================
CREATE TABLE api_rate_limits (
    company_id      UUID         NOT NULL,
    window_started  TIMESTAMPTZ  NOT NULL,
    request_count   INTEGER      NOT NULL DEFAULT 0,
    blocked_count   INTEGER      NOT NULL DEFAULT 0,
    PRIMARY KEY (company_id, window_started)
);

-- =============================================================================
-- updated_at trigger
-- =============================================================================
CREATE OR REPLACE FUNCTION trg_set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER companies_set_updated_at BEFORE UPDATE ON companies
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

CREATE TRIGGER shipments_set_updated_at BEFORE UPDATE ON shipments
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
