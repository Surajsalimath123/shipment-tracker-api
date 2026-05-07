# Shipment Tracker API

Real-time shipment event tracking API for a logistics platform. Records carrier
events (pickup, in-transit, delivery), exposes a unified status view, and
notifies subscribed external systems via signed webhooks.

Built for the **Argus Logistics Senior API Developer assessment**.

---

## Tech Stack

| Layer | Choice |
|---|---|
| Runtime | Java 17, Spring Boot 3.5.14 |
| Web | Spring MVC, Spring Validation |
| Data | Spring Data JPA, Hibernate, PostgreSQL 16 |
| Migrations | Flyway |
| Security | Spring Security, JWT (jjwt) |
| Rate limiting | Bucket4j (token bucket) |
| Async | `@Async` + Spring Retry (webhook delivery) |
| Docs | springdoc-openapi (OpenAPI 3.0 + Swagger UI) |
| Testing | JUnit 5, Mockito, TestContainers, MockMvc |
| Coverage | JaCoCo |
| Build | Maven 3.9 (via wrapper) |
| DevOps | Docker (multi-stage), docker-compose, GitHub Actions |

---

## Quick Start

### Prerequisites

- Java 17+ JDK
- Docker (for local Postgres and TestContainers)

### 1. Start Postgres locally

```bash
docker compose up -d postgres
```

### 2. Run the API

```bash
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`. Swagger UI is at
`http://localhost:8080/swagger-ui.html`.

### 3. Run all tests

```bash
./mvnw verify
```

JaCoCo coverage report: `target/site/jacoco/index.html`.

### 4. Run everything in Docker (build + DB + API)

```bash
docker compose --profile full up --build
```

---

## Project Layout

```
src/main/java/com/argus/shipmenttracker/
├── controller/   - REST controllers (thin)
├── service/      - Business logic, tenant enforcement
├── repository/   - Spring Data JPA, tenant-scoped queries
├── domain/       - JPA entities (mirror DB schema)
├── dto/          - API request / response contracts
├── security/     - JWT, Spring Security, TenantContext
├── webhook/      - Async dispatcher with retry + HMAC signing
├── ratelimit/    - Per-tenant Bucket4j filter
├── config/       - Cross-cutting Spring configuration
└── exception/    - Domain exceptions + RFC 7807 advice
```

---

## Documentation

- `API_DESIGN.md` — OpenAPI 3.0 spec, auth flow, rate limits, error codes
- `ARCHITECTURE.md` — schema design, partitioning, scaling path, trade-offs
- `db/schema.sql` — annotated PostgreSQL schema with indexes and partitions

---

## Status

🚧 In active development for the May 2026 assessment.
