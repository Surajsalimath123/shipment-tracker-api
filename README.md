# Shipment Tracker API

Real-time shipment event tracking API for a logistics platform. Records
carrier events (pickup, in-transit, delivery), exposes a unified status
view, and notifies subscribed external systems via signed webhooks.

Multi-tenant, JWT-authenticated, rate-limited, and built for scale —
target: **10,000 events/minute per instance**.

> Built for the **Argus Logistics Senior API Developer assessment** (May 2026).

---

## Table of Contents

1. [Tech Stack](#tech-stack)
2. [Quickstart](#quickstart)
3. [Endpoints at a Glance](#endpoints-at-a-glance)
4. [Demo Walkthrough](#demo-walkthrough-with-curl)
5. [Running the Tests](#running-the-tests)
6. [Project Layout](#project-layout)
7. [Documentation](#documentation)
8. [Configuration](#configuration)

---

## Tech Stack

| Layer | Choice |
|---|---|
| Runtime | Java 17 (LTS), Spring Boot 3.5.14 |
| Web | Spring MVC + Spring Validation |
| Data | Spring Data JPA, Hibernate 6.6, PostgreSQL 16 |
| Migrations | Flyway (versioned SQL) |
| Security | Spring Security 6 + jjwt 0.12 (HS256 JWT) |
| Rate limiting | Bucket4j 8.10 (token bucket, in-memory) |
| Async dispatch | `@Async` + Spring Retry + bounded `ThreadPoolTaskExecutor` |
| API docs | springdoc-openapi 2.6 (OpenAPI 3.0 + Swagger UI) |
| Testing | JUnit 5, Mockito, AssertJ, TestContainers, MockMvc |
| Coverage | JaCoCo (80% gate enforced at `verify` phase) |
| Build | Maven 3.9 (via wrapper — no install required) |
| Containers | Multi-stage Dockerfile, `docker-compose.yml` |

---

## Quickstart

**Prerequisites**: Java 17 JDK, Docker.

### 1. Clone and start the database

```bash
git clone https://github.com/Surajsalimath123/shipment-tracker-api.git
cd shipment-tracker-api
docker compose up -d postgres
```

Postgres 16 is now listening on `localhost:5432`. Flyway will create the
schema and seed two demo tenants on first boot.

### 2. Run the API

```bash
./mvnw spring-boot:run
```

Or build a fat JAR and run it:

```bash
./mvnw clean package -DskipTests
java -jar target/shipment-tracker-api-0.0.1-SNAPSHOT.jar
```

### 3. Open Swagger UI

http://localhost:8080/swagger-ui.html

Click "Authorize" and paste the `accessToken` from `/api/v1/auth/token`
— every endpoint becomes interactive.

### 4. Tear down

```bash
docker compose down            # stop containers
docker compose down -v         # also wipe Postgres volume (start fresh)
```

---

## Demo Tenants

Two companies are seeded by Flyway migration `V3__seed_demo_data.sql`.

| Company | UUID | Demo API Key |
|---|---|---|
| **Acme Logistics** | `11111111-1111-1111-1111-111111111111` | `demo-key-acme-12345` |
| **FastFreight Corp** | `22222222-2222-2222-2222-222222222222` | `demo-key-fast-67890` |

Acme has shipments `SHP-DEMO-001` and `SHP-DEMO-002`. FastFreight has
`SHP-FF-1001`. The tenants cannot read each other's shipments — that
isolation is verified by an integration test.

---

## Endpoints at a Glance

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/auth/token` | Exchange `companyId` + `apiKey` for a JWT |
| `POST` | `/api/v1/shipments/{id}/events` | Record a new event |
| `GET`  | `/api/v1/shipments/{id}/events` | Paginated event history |
| `GET`  | `/api/v1/shipments/{id}/status` | Current status snapshot |
| `POST` | `/api/v1/webhooks` | Subscribe to event notifications |
| `DELETE` | `/api/v1/webhooks/{id}` | Unsubscribe |

Health & monitoring (unauthenticated):

| Path | Purpose |
|---|---|
| `/actuator/health` | Liveness / readiness |
| `/actuator/prometheus` | Metrics scrape endpoint |
| `/v3/api-docs` | OpenAPI 3.0 JSON |
| `/swagger-ui.html` | Interactive UI |

Full API contract: **[API_DESIGN.md](API_DESIGN.md)**.

---

## Demo Walkthrough with curl

```bash
# 1. Get a token
TOKEN=$(curl -sS -X POST http://localhost:8080/api/v1/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"companyId":"11111111-1111-1111-1111-111111111111","apiKey":"demo-key-acme-12345"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')

# 2. Record an event
curl -sS -X POST http://localhost:8080/api/v1/shipments/SHP-DEMO-001/events \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "eventType": "IN_TRANSIT",
    "timestamp": "2026-05-07T14:30:00Z",
    "location": {"latitude": 40.7128, "longitude": -74.0060, "address": "New York, NY"},
    "metadata": {"carrier": "Acme Trucking", "vehicle": "TRUCK-42"}
  }'

# 3. Read current status (should reflect IN_TRANSIT)
curl -sS http://localhost:8080/api/v1/shipments/SHP-DEMO-001/status \
  -H "Authorization: Bearer $TOKEN"

# 4. List events (paginated)
curl -sS 'http://localhost:8080/api/v1/shipments/SHP-DEMO-001/events?page=0&size=10' \
  -H "Authorization: Bearer $TOKEN"

# 5. Subscribe to webhooks
curl -sS -X POST http://localhost:8080/api/v1/webhooks \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://webhook.site/your-uuid","eventTypes":["DELIVERED","EXCEPTION"]}'

# 6. Multi-tenant isolation: Acme can't see FastFreight's shipment
curl -sS -o /dev/null -w 'HTTP %{http_code}\n' \
  http://localhost:8080/api/v1/shipments/SHP-FF-1001/status \
  -H "Authorization: Bearer $TOKEN"
# -> HTTP 404
```

---

## Running the Tests

```bash
# All tests, plus JaCoCo coverage report
./mvnw verify

# Just unit tests, fast loop while developing
./mvnw test -Dtest='*Test' -DskipITs

# Open the coverage report after `verify`
open target/site/jacoco/index.html
```

Tests use **TestContainers** — Docker must be running. Spring Boot's
`@ServiceConnection` wires the test datasource to a real Postgres 16
container automatically; no manual DB setup needed.

**Current coverage**: 82.3% overall, 100% for controllers, 99.7% for
services. The build fails if coverage drops below 80%.

### Run everything in containers (build + DB + API)

```bash
docker compose --profile full up --build
```

The multi-stage Dockerfile builds the JAR in a JDK image and runs it from
a slim JRE image (`~200 MB`). Layered jar extraction means dependency
layers are cached on rebuild.

---

## Project Layout

```
src/main/java/com/argus/shipmenttracker/
├── controller/   - REST controllers (thin, delegate to service)
├── service/      - business logic, transactions, tenant enforcement
├── repository/   - Spring Data JPA, all queries scoped by company_id
├── domain/       - JPA entities (mirror DB)
├── dto/          - request / response records (decoupled from entities)
├── security/     - JWT filter, SecurityConfig, TenantContext
├── webhook/      - async dispatcher, signer, payload, delivery logs
├── ratelimit/    - Bucket4j filter and per-tenant registry
├── config/       - Spring beans (async executor, OpenAPI)
└── exception/    - custom exceptions + RFC 7807 error advice

src/main/resources/
├── application.yml                       (dev + test profiles)
└── db/migration/                         (Flyway migrations)
    ├── V1__initial_schema.sql
    ├── V2__create_event_partitions.sql
    └── V3__seed_demo_data.sql

src/test/java/com/argus/shipmenttracker/
├── domain/       - enum & entity unit tests
├── security/     - JWT + TenantContext unit tests
├── webhook/      - HMAC signer unit tests
├── service/      - mocked unit tests for AuthService, ShipmentEventService, WebhookService
├── ratelimit/    - bucket exhaustion tests
└── integration/  - ShipmentApiIntegrationTest (full HTTP flow with TestContainers)

db/schema.sql     - single-file schema reference (matches V1 + V2)

API_DESIGN.md     - OpenAPI 3.0 spec, auth flow, rate limits, error codes
ARCHITECTURE.md   - schema rationale, scaling path, trade-offs
Dockerfile        - multi-stage build (JDK -> JRE, layered jar)
docker-compose.yml - Postgres + (optional) API service
.github/workflows/ - GitHub Actions CI (build, test, coverage)
```

---

## Documentation

| Document | Contents |
|---|---|
| **[API_DESIGN.md](API_DESIGN.md)** | OpenAPI 3.0 spec (YAML), auth flow diagram, rate-limit strategy, error codes, validation rules, webhook delivery contract |
| **[ARCHITECTURE.md](ARCHITECTURE.md)** | System overview, schema design rationale, multi-tenancy approach, async dispatch pattern, scaling path, trade-offs |
| **[db/schema.sql](db/schema.sql)** | Annotated PostgreSQL schema with indexes, partitions, and triggers |
| **OpenAPI live** | http://localhost:8080/v3/api-docs (when running) |
| **Swagger UI** | http://localhost:8080/swagger-ui.html (when running) |

---

## Configuration

All configuration is in `src/main/resources/application.yml`. Override via
environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/shipment_tracker` | Database URL |
| `DB_USERNAME` | `postgres` | DB user |
| `DB_PASSWORD` | `postgres` | DB password |
| `JWT_SECRET` | dev placeholder | Base64-encoded HS256 key, ≥ 32 bytes |
| `JWT_EXPIRATION_MS` | `3600000` (1 hour) | Token TTL |
| `WEBHOOK_SIGNING_SECRET` | dev placeholder | HMAC key for outbound webhooks |
| `SERVER_PORT` | `8080` | HTTP listen port |
| `SPRING_PROFILES_ACTIVE` | (none) | Use `test` to enable TestContainers JDBC URL |

> **Production note**: every secret in this list MUST be rotated and
> stored in a secret manager. The defaults are explicitly marked
> `dev-only-*` so a misconfigured production deploy fails loudly.

---

## License

Proprietary — assessment submission.

---

🤖 Built with care for the Argus Logistics review on May 11, 2026.
