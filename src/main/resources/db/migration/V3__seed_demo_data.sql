-- =============================================================================
-- V3: Seed demo data
--
-- Creates two test companies plus sample shipments so the API is usable
-- immediately after a fresh `docker compose up`. Plaintext API keys are
-- documented in README.md (Quickstart section).
--
-- The api_key_hash uses pgcrypto's crypt(plaintext, gen_salt('bf', 10)).
-- That generates a $2a$10$... hash which Spring Security's
-- BCryptPasswordEncoder verifies natively.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Companies (api keys: see README.md)
-- ---------------------------------------------------------------------------
INSERT INTO companies (company_id, name, api_key_hash, is_active) VALUES
    ('11111111-1111-1111-1111-111111111111',
     'Acme Logistics',
     crypt('demo-key-acme-12345', gen_salt('bf', 10)),
     TRUE),
    ('22222222-2222-2222-2222-222222222222',
     'FastFreight Corp',
     crypt('demo-key-fast-67890', gen_salt('bf', 10)),
     TRUE);

-- ---------------------------------------------------------------------------
-- Shipments
-- ---------------------------------------------------------------------------
INSERT INTO shipments
    (company_id, shipment_id, origin, destination, carrier,
     current_status, estimated_eta, metadata)
VALUES
    ('11111111-1111-1111-1111-111111111111',
     'SHP-DEMO-001',
     'New York, NY',
     'Los Angeles, CA',
     'Acme Trucking',
     'CREATED',
     now() + INTERVAL '4 days',
     '{"weight_kg": 1250, "service": "standard"}'),

    ('11111111-1111-1111-1111-111111111111',
     'SHP-DEMO-002',
     'Chicago, IL',
     'Houston, TX',
     'Acme Trucking',
     'CREATED',
     now() + INTERVAL '2 days',
     '{"weight_kg": 480, "service": "express"}'),

    ('22222222-2222-2222-2222-222222222222',
     'SHP-FF-1001',
     'Seattle, WA',
     'Boston, MA',
     'FastFreight Cargo',
     'CREATED',
     now() + INTERVAL '5 days',
     '{"weight_kg": 2100, "service": "freight", "hazmat": false}');

-- ---------------------------------------------------------------------------
-- Sample webhook subscriptions
-- ---------------------------------------------------------------------------
INSERT INTO webhooks (company_id, url, event_types, secret, description) VALUES
    ('11111111-1111-1111-1111-111111111111',
     'https://webhook.site/acme-demo-endpoint',
     ARRAY['*'],
     'acme-webhook-secret-rotate-me',
     'Acme Logistics demo webhook (catches all event types)'),
    ('22222222-2222-2222-2222-222222222222',
     'https://webhook.site/fastfreight-demo-endpoint',
     ARRAY['DELIVERED', 'EXCEPTION'],
     'fastfreight-webhook-secret-rotate-me',
     'FastFreight: only notify on DELIVERED and EXCEPTION');
