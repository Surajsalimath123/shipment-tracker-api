# Architecture

> Design decisions, schema rationale, scaling plan, and trade-offs for the
> Shipment Tracker API. Read this *with* `db/schema.sql` and `API_DESIGN.md`
> open — they are the authoritative spec; this document explains *why*.

---

## Table of Contents

1. [System Overview](#system-overview)
2. [Layered Architecture](#layered-architecture)
3. [Database Schema](#database-schema)
4. [Multi-Tenancy](#multi-tenancy)
5. [Async Webhook Delivery](#async-webhook-delivery)
6. [Rate Limiting](#rate-limiting)
7. [Scaling Path](#scaling-path-from-mvp-to-production)
8. [Trade-offs and What's Deferred](#trade-offs-and-whats-deferred)

---

## System Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│ Carrier systems / internal tools                                     │
└────────────────────────┬─────────────────────────────────────────────┘
                         │ HTTPS, JWT bearer
                         ▼
┌──────────────────────────────────────────────────────────────────────┐
│ Shipment Tracker API  (Spring Boot 3.5, Java 17)                     │
│                                                                      │
│  Filter chain:  JwtAuthenticationFilter → RateLimitFilter            │
│                                                                      │
│  Controllers (thin, validation-only)                                 │
│      │                                                               │
│      ▼                                                               │
│  Services  (business rules, transactions, tenant enforcement)        │
│      │                                                               │
│      ├─────────► Repositories ─► PostgreSQL 16 (partitioned)         │
│      │                                                               │
│      └─► AFTER_COMMIT event ─► AsyncWebhookDispatcher                │
│                                  │                                   │
│                                  ▼                                   │
│                         External webhook subscribers                 │
└──────────────────────────────────────────────────────────────────────┘
```

The API is a single Spring Boot service. PostgreSQL is the only required external dependency. Webhook delivery is in-process on a bounded thread pool — no message broker required for the MVP.

---

## Layered Architecture

Strict layering is enforced through package structure:

```
com.argus.shipmenttracker
├── controller/   - HTTP only: validate input, delegate to service, shape DTO
├── service/      - business rules, transactions, tenant context enforcement
├── repository/   - Spring Data JPA, queries scoped by company_id
├── domain/       - JPA entities (mirror DB)
├── dto/          - public API contract (records, decoupled from entities)
├── security/     - JWT filter, SecurityConfig, TenantContext
├── webhook/      - async dispatcher, signer, payload, delivery logs
├── ratelimit/    - Bucket4j filter and registry
├── config/       - cross-cutting (async executor, OpenAPI)
└── exception/    - domain exceptions + RFC 7807 advice
```

### Why DTOs separate from entities

The public API contract should not change just because we re-shape the
internal schema. DTOs let us evolve the entity model freely (rename a
column, denormalize a field) without breaking clients.

### Why repositories accept `companyId` as a parameter

We never trust the URL or the authenticated principal at the repository
layer alone — the service layer pulls `companyId` from `TenantContext` and
passes it explicitly. This makes tenant isolation a *visible* part of every
query signature, not an invisible side effect of an interceptor that could
fail silently.

---

## Database Schema

```
┌────────────┐
│ companies  │ ← tenant root
└─────┬──────┘
      │ 1..*
      ├──────────────────────────────────────────────────────┐
      ▼                                                      ▼
┌────────────┐                                       ┌────────────┐
│ shipments  │ ← composite PK (company_id, id)       │  webhooks  │
└─────┬──────┘                                       └─────┬──────┘
      │ 1..*                                               │ 1..*
      ▼                                                    ▼
┌─────────────────────────┐                  ┌──────────────────────────┐
│ shipment_events         │                  │ webhook_delivery_logs    │
│ (PARTITIONED, monthly)  │                  │ (PARTITIONED, monthly)   │
└─────────────────────────┘                  └──────────────────────────┘
```

### Why partition `shipment_events`

At 10,000 events/minute the table grows by **~430 million rows per month**.
On an unpartitioned table:
- VACUUM time grows linearly with table size.
- Index B-trees become deeper, so each insert pays more.
- Queries that don't include a date predicate scan the whole table.

Monthly RANGE partitioning solves all three:
- Each partition is small (~430M rows max), so VACUUM stays fast.
- Indexes are per-partition, so insert cost is bounded.
- Queries for a specific shipment naturally include `occurred_at` ranges,
  so Postgres prunes irrelevant partitions.
- Old partitions can be **detached and archived** to cold storage in O(1).

The same logic applies to `webhook_delivery_logs`.

### Composite PK on partitioned tables

Postgres requires the partition key to be part of any unique constraint.
On `shipment_events`, that means PK = `(event_id, occurred_at)`. We treat
`event_id` (a UUID) as the logical identifier — UUID collisions are
negligible — and JPA uses just `event_id` as `@Id`. This works because
the application never queries events by `event_id` alone; the access
pattern is always `(company_id, shipment_id, time-range)`.

### Indexing strategy

| Table | Index | Why |
|---|---|---|
| `shipment_events` | `(company_id, shipment_id, occurred_at DESC)` | Covers GET /events; Postgres uses index-only scan |
| `shipment_events` | `(company_id, received_at DESC)` | Cross-shipment recent activity (operator dashboard) |
| `shipment_events` | GIN on `metadata` | Flexible carrier-specific search (`metadata @> '{"vehicle":"TRUCK-789"}'`) |
| `shipments` | `(company_id, current_status)` | "How many in transit?" dashboards |
| `shipments` | partial `(company_id, estimated_eta) WHERE eta IS NOT NULL` | ETA-bucket reports without scanning unscheduled rows |
| `webhook_delivery_logs` | partial `(company_id, delivered_at) WHERE success = false` | Failure dashboards with minimal index size |

### Denormalization on `shipments`

`current_status`, `last_event_at`, and `last_location` are derived from
events. We **denormalize them onto `shipments`** so the `/status` endpoint
is a single primary-key lookup instead of a `MAX(occurred_at)` aggregation
across millions of events. The trade-off is that the application must keep
both rows in sync — this happens inside the `recordEvent` transaction, so
a partial update is impossible.

### JSONB for `location` and `metadata`

Both fields hold semi-structured data that varies by carrier. JSONB gives
us:
- Schema flexibility without ALTER TABLE migrations for every new carrier
- Native indexing via GIN
- Queryability with `->`, `->>`, and `@>` operators

The location columns could have been split into typed `latitude` /
`longitude` / `address`, but the assessment explicitly asks for JSONB — and
real carriers send wildly different location shapes (geofenced regions,
Plus Codes, what3words, etc.).

---

## Multi-Tenancy

### Approach: shared schema with `company_id` discriminator

Three options were considered:

| Approach | Pros | Cons |
|---|---|---|
| Database-per-tenant | Strongest isolation | Operational nightmare at thousands of tenants |
| Schema-per-tenant | Good isolation | Migration complexity grows with tenant count |
| **Shared schema with `company_id`** | Single migration, simple ops, easy joins | Bug in a query can leak data |

We chose the shared-schema approach **with belt-and-suspenders defenses** to compensate for its risk:

1. **JWT carries `company_id`** as a custom claim
2. **`JwtAuthenticationFilter`** populates `TenantContext` (a thread-local)
3. **Every service entry point** calls `TenantContext.requireCompanyId()`
4. **Every repository query** takes `companyId` as a parameter
5. **Composite PK on `shipments`** = `(company_id, shipment_id)` — even a
   bug that loses the `company_id` filter cannot return another tenant's
   row, because the same `shipment_id` in a different tenant is a different
   row.
6. **Tenant isolation tests** explicitly assert that company A cannot
   read or write company B's resources.

### Future hardening (deferred): Postgres Row-Level Security

The natural next layer is **RLS policies** on every domain table:

```sql
ALTER TABLE shipments ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON shipments
    USING (company_id = current_setting('app.tenant')::uuid);
```

The application sets `SET LOCAL app.tenant = '...'` at the start of each
transaction. After this, any forgotten `WHERE company_id = ?` clause is
*physically impossible* to leak data — the database refuses.

Not implemented in v1 because Spring + JPA + RLS requires a connection-pool
hook (every connection check-out runs `SET LOCAL`), which adds complexity.
Listed as the top production-hardening task.

---

## Async Webhook Delivery

### Why async, why a thread pool, why not a queue

| Option | Why we chose / rejected |
|---|---|
| Synchronous in the request path | A slow subscriber would balloon API latency. Rejected. |
| **`@Async` on a bounded thread pool** | Decouples API latency from subscriber latency. ✅ Chosen for v1. |
| Message broker (RabbitMQ / Kafka) | Survives restarts, supports back-pressure, durable. The right answer at production scale; deferred to keep dependencies minimal for the assessment. |

### Bounded pool with CallerRunsPolicy

```
core: 4    max: 16    queue: 1,000    rejection: CallerRunsPolicy
```

If the queue saturates, the calling thread runs the task itself. This
applies back-pressure to the API instead of dropping events. Under
sustained pressure, API latency degrades gracefully and the surrounding
infrastructure (load balancer, auto-scaler) takes notice.

### Retry policy

5 attempts with exponential backoff: 1s → 2s → 4s → 8s → 16s, max 60s
(configurable in `application.yml`). Every attempt is logged to
`webhook_delivery_logs`, so operators can:
- Debug why a webhook failed without re-running
- Disable subscribers whose `consecutive_failures` exceeds a threshold
- Replay deliveries from the audit table

### `@TransactionalEventListener(AFTER_COMMIT)`

The dispatcher fires from a Spring application event published *inside*
`recordEvent`. We listen with `phase = AFTER_COMMIT` so the webhook is
dispatched **only if** the database transaction committed. A rolled-back
event must never trigger a notification — that's how subscribers end up
processing ghost events.

### HMAC signing

Outbound bodies carry `X-Shipment-Tracker-Signature: sha256=<hex>` — HMAC
of the body using the webhook's secret. Subscribers verify this header
plus the timestamp to prevent replay. The secret is generated server-side
(32 random bytes, URL-safe base64), returned exactly once at creation, and
stored in plaintext (it's the secret) — never returned again.

---

## Rate Limiting

### Token bucket via Bucket4j

Each tenant has a Bucket4j `Bucket`. Buckets are created lazily on first
request and cached in a `ConcurrentHashMap<UUID, Bucket>`.

| Why token bucket vs leaky bucket vs fixed window |
|---|
| **Token bucket** allows short bursts (good UX) on a steady refill rate. |
| Leaky bucket smooths traffic but doesn't allow bursts. |
| Fixed window has thundering-herd at the boundary. |

### Configuration

| Tier | Steady | Burst |
|---|---|---|
| Authenticated | 1,000 req/min | 100 |
| Unauthenticated | 100 req/min | 20 |

### Multi-instance scaling

In-memory buckets work for a single instance. For replicas, swap to
**Bucket4j-Redis**: same API, but bucket state lives in Redis with atomic
Lua-script operations. All replicas see the same view.

The `api_rate_limits` table is reserved for *audit* (which tenant hit
limits, and how often) — Bucket4j is the *enforcement* layer. Not currently
written to per-request because per-request DB writes would defeat the
purpose; instead it's intended for batch flushes from metrics in a future
build.

---

## Scaling Path: from MVP to Production

The current build targets 10,000 events/minute on a single instance. To
scale beyond that:

| Scale step | Add |
|---|---|
| **2× current** | Vertical scale: bigger Postgres, more API CPU, more pool connections. Nothing in code changes. |
| **5×** | Read replicas for `/events` and `/status` queries. Spring Data JPA `@Transactional(readOnly = true)` already routes correctly when configured. |
| **10×** | Move webhook dispatch to a queue (RabbitMQ/Kafka). API publishes; a separate worker pool consumes. Survives API restarts; trivially scalable. |
| **50×** | Shard the `shipments` and `shipment_events` tables by `company_id` hash. Postgres declarative partitioning by hash range supports this without app changes if the access pattern always includes `company_id`. |
| **100×+** | Split off a dedicated `event-ingest` service. The current API becomes a thin facade. |

### Production observability (deferred)

| Concern | Tool I'd add |
|---|---|
| Metrics | Micrometer + Prometheus (`/actuator/prometheus` endpoint already exposed) |
| Distributed tracing | Micrometer Tracing + OTLP exporter (built into Spring Boot 3) |
| Structured logging | Logback JSON encoder + Logstash forwarder |
| Webhook dashboards | Grafana panel against `webhook_delivery_logs` (failure rate, latency P95) |

---

## Trade-offs and What's Deferred

### Intentional trade-offs

1. **Single instance / in-memory rate limiting.** Production will use
   Bucket4j-Redis. The interface doesn't change.
2. **Async webhook in-process.** A queue is the right answer above ~10k
   subscribers. The dispatcher is a Spring bean — easy to swap.
3. **Pre-created partitions through Dec 2026.** Production needs a
   scheduled job (or `pg_partman`) to roll forward.
4. **`SET LOCAL app.tenant` + RLS not enabled yet.** App-level enforcement
   plus composite PKs cover the same ground for v1.
5. **No refresh tokens.** Clients re-auth before the 1-hour expiration.
   Refresh-token flow is documented in API_DESIGN.md as future work.

### What I'd add given another sprint

| Priority | Item | Estimate |
|---|---|---|
| ★★★ | Postgres RLS policies + connection-pool hook for `app.tenant` | 1 day |
| ★★★ | Distributed tracing (Micrometer Tracing + OTLP) | 0.5 day |
| ★★ | Bucket4j-Redis for distributed rate limiting | 0.5 day |
| ★★ | Resilience4j circuit breaker on webhook outbound calls | 0.5 day |
| ★★ | Helm chart + values.yaml for K8s deploy | 1 day |
| ★ | Refresh tokens + key rotation | 1 day |
| ★ | Event sourcing replay for backfills | 2 days |

### What I deliberately did NOT add

- **Custom metrics tables.** `webhook_delivery_logs` and Postgres pg_stat
  views cover the visibility we need.
- **An ORM cache.** Cache-coherence bugs cost more debugging time than
  cache hits save.
- **CQRS read models.** The denormalization on `shipments` covers the only
  read path that matters; full CQRS would be over-engineering.
- **Saga / outbox pattern.** Webhooks are best-effort with retry, not part
  of a multi-step business transaction. Outbox would solve a problem we
  don't have.
