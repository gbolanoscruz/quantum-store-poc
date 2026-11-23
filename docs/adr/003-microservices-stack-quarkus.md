# ADR-003: Standardize Backend Microservices on Quarkus (Java 21 LTS)

- **Status:** Accepted *(Validated in production with `catalog-service` and `inventory-service`)*
- **Date:** 2026-11-22 *(Updated 2026-11-23 to align with [ADR-004](004-migrate-backend-to-cloud-run.md))*
- **Deciders:** `@gbolanoscruz`

## Context

With the $0 infrastructure, DevSecOps guardrails, and automated CI/CD pipeline in place (originally OCI per ADR-001/002, later migrated to Google Cloud Run per [ADR-004](004-migrate-backend-to-cloud-run.md)), `quantum-store-poc` transitioned from the initial `hello-service` PoC skeleton into real e-commerce microservices (`catalog-service`, `inventory-service`, and upcoming `orders-service` and `payments-service`).

The backend framework must satisfy two core requirements:
1. **Low memory footprint & fast startup:** Backend containers target $0 serverless tiers (originally a 1 GB RAM OCI VM, now **Google Cloud Run** at 512 MiB RAM per service per ADR-004), where lean memory usage and fast startup directly minimize *scale-to-zero* cold-start latency.
2. **Modern cloud-native Java ecosystem:** First-class support for containers, REST APIs, typed inter-service clients, schema migrations, OpenAPI, health checks, observability, and eventual native compilation (GraalVM) without sacrificing developer productivity.

## Decision

Standardize 100% of backend microservices on **Quarkus 3.40 LTS** running on **Java 21 LTS** (Maven):

- **Runtime & Hardening Strategy:** Run **Quarkus Fast-JAR (JVM mode)** on Alpine JRE (`eclipse-temurin:21-jre-alpine`) with build-time OS patching (`apk update && apk upgrade --no-cache` for Trivy compliance), a non-root `quarkus` runtime user, dynamic port binding (`quarkus.http.port=${PORT:808x}`), and 512 MiB Cloud Run containers (`-XX:MaxRAMPercentage=75.0`), keeping the door open to **GraalVM native executables** if sub-second cold starts become necessary.
- **Core Extensions:**
  - `quarkus-rest-jackson` (REST endpoints + JSON serialization)
  - `quarkus-rest-client-jackson` (typed inter-service HTTP calls using Cloud Run HTTPS URLs injected at deploy time, e.g., `CATALOG_SERVICE_URL`)
  - `quarkus-hibernate-orm-panache` + `quarkus-jdbc-postgresql` (concise persistence layer)
  - `quarkus-flyway` (versioned SQL schema migrations and seed data at startup)
  - `quarkus-hibernate-validator` (Bean Validation on request payloads)
  - `quarkus-smallrye-health` (liveness/readiness probes at `/q/health/ready` for Cloud Run and CI/CD smoke tests)
  - `quarkus-smallrye-openapi` (auto-generated Swagger/OpenAPI contracts)
- **Data & Messaging Offloading:**
  - **Persistence:** Offloaded to **Neon Serverless PostgreSQL ($0)** following the **Database-per-Service** pattern (`catalog_db`, `inventory_db`, etc.) with zero cross-service foreign keys or shared tables.
  - **Messaging (Phase 2):** Future event-driven brokers (e.g., Upstash Redis Streams, NATS, or CloudAMQP) will also be offloaded to $0 managed tiers to keep Cloud Run microservices completely stateless.

## Alternatives Considered

| Option | Pros | Reason Rejected |
| :--- | :--- | :--- |
| **Spring Boot (3.x / 4.x)** | Dominant enterprise adoption and ATS keyword recognition | Higher baseline JVM memory consumption (~250–400 MB per service with JPA/Hikari) and slower cold starts in serverless scale-to-zero environments. |
| **Hybrid (Spring Boot + Quarkus)** | Demonstrates multi-framework versatility | Doubles maintenance overhead (different BOMs, CI templates, observability/security configs) for a solo PoC. |
| **Raw JDK (`HttpServer`)** | Minimal footprint (used in the retired `hello-service` PoC) | Lacks built-in DI, ORM, Flyway, REST Client, OpenAPI, validation, and health probes needed for production-grade microservices. |

## Consequences

### Positive
- **Lean footprint & fast serverless startup:** With Hibernate ORM, Flyway, and PostgreSQL JDBC active, each Quarkus JVM service runs steadily at **~170–185 MB RAM** (leaving ample headroom inside Cloud Run's 512 MiB allocation) and boots in ~2–4 seconds with `--cpu-boost`.
- **Container-first developer experience:** Fast builds, clean multi-stage Dockerfiles, and uniform conventions across all services in `/services/*`.
- **Strong architectural differentiation:** Demonstrates pragmatic, constraint-driven engineering (*right tool for the job*) rather than defaulting to a framework blindly.

### Negative & Mitigations
- **Smaller job-market share than Spring Boot:** Mitigated by applying transferable enterprise patterns (Database-per-Service, REST/OpenAPI, Panache/JPA, Flyway, REST Client, CI/CD, DevSecOps, event-driven design) that map directly to Spring ecosystems.
- **Cold starts on serverless scale-to-zero:** Mitigated via Quarkus's Fast-JAR startup profile and Cloud Run's `--cpu-boost`, with GraalVM native compilation available if tighter latency SLAs are required.
- **GraalVM native builds consume high RAM/CPU:** Mitigated by keeping GitHub Actions runners (7 GB RAM) responsible for all Docker builds and defaulting to Fast-JAR until native compilation is explicitly needed.