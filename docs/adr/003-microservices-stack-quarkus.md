# ADR-003: Standardize Backend Microservices on Quarkus (Java 21 LTS)

- **Status:** Accepted *(Validated in production with `catalog-service` and `inventory-service`)*
- **Date:** 2026-11-22
- **Deciders:** `@gbolanoscruz`

## Context

With the $0 hybrid infrastructure, DevSecOps guardrails, and automated GHCR/SSH deployment pipeline in place (ADR-001, ADR-002), `quantum-store-poc` transitioned from the initial `hello-service` PoC skeleton into real e-commerce microservices (`catalog-service`, `inventory-service`, and upcoming `orders-service` and `payments-service`).

The backend framework must satisfy two core requirements:
1. **Low memory footprint & predictable resource usage:** All backend containers run on a single Oracle Cloud Always Free `VM.Standard.E2.1.Micro` instance (1 OCPU, 1 GB RAM + 2.5 GB swap).
2. **Modern cloud-native Java ecosystem:** First-class support for containers, REST APIs, typed inter-service clients, schema migrations, OpenAPI, health checks, observability, and eventual native compilation (GraalVM) without sacrificing developer productivity.

## Decision

Standardize 100% of backend microservices on **Quarkus 3.40 LTS** running on **Java 21 LTS** (Maven):

- **Runtime & Hardening Strategy:** Run **Quarkus Fast-JAR (JVM mode)** on Alpine JRE (`eclipse-temurin:21-jre-alpine`) with build-time OS patching (`apk update && apk upgrade --no-cache` for Trivy compliance), a non-root `quarkus` runtime user, and explicit container memory caps (`--memory="256m"`, `-XX:MaxRAMPercentage=75.0`), keeping the door open to **GraalVM native executables** if service density requires sub-50 MB footprints.
- **Core Extensions:**
  - `quarkus-rest-jackson` (REST endpoints + JSON serialization)
  - `quarkus-rest-client-jackson` (typed inter-service HTTP calls over the internal Docker network `quantum-net`)
  - `quarkus-hibernate-orm-panache` + `quarkus-jdbc-postgresql` (concise persistence layer)
  - `quarkus-flyway` (versioned SQL schema migrations and seed data at startup)
  - `quarkus-hibernate-validator` (Bean Validation on request payloads)
  - `quarkus-smallrye-health` (liveness/readiness probes at `/q/health/ready` for CI/CD and watchdogs)
  - `quarkus-smallrye-openapi` (auto-generated Swagger/OpenAPI contracts)
- **Data & Messaging Offloading:**
  - **Persistence:** Offloaded to **Neon Serverless PostgreSQL ($0)** following the **Database-per-Service** pattern (`catalog_db`, `inventory_db`, etc.) with zero cross-service foreign keys or shared tables.
  - **Messaging (Phase 2):** Future event-driven brokers (e.g., Upstash Redis Streams, NATS, or CloudAMQP) will also be offloaded to $0 managed tiers rather than consuming OCI RAM.

## Alternatives Considered

| Option | Pros | Reason Rejected |
| :--- | :--- | :--- |
| **Spring Boot (3.x / 4.x)** | Dominant enterprise adoption and ATS keyword recognition | Higher baseline JVM memory consumption (~250–400 MB per service with JPA/Hikari), making it harder to co-locate multiple microservices on a 1 GB RAM VM without heavy swapping. |
| **Hybrid (Spring Boot + Quarkus)** | Demonstrates multi-framework versatility | Doubles maintenance overhead (different BOMs, CI templates, observability/security configs) for a solo PoC. |
| **Raw JDK (`HttpServer`)** | Minimal footprint (used in the retired `hello-service` PoC) | Lacks built-in DI, ORM, Flyway, REST Client, OpenAPI, validation, and health probes needed for production-grade microservices. |

## Consequences

### Positive
- **Predictable density on 1 GB RAM:** With Hibernate ORM, Flyway, and PostgreSQL JDBC active, each Quarkus JVM service runs steadily at **~170–185 MB RAM** (safely within its `256m` container cap), allowing multiple microservices plus Caddy to coexist on the Always Free VM.
- **Container-first developer experience:** Fast builds, clean multi-stage Dockerfiles, and uniform conventions across all services in `/services/*`.
- **Strong architectural differentiation:** Demonstrates pragmatic, constraint-driven engineering (*right tool for the job*) rather than defaulting to a framework blindly.

### Negative & Mitigations
- **Smaller job-market share than Spring Boot:** Mitigated by applying transferable enterprise patterns (Database-per-Service, REST/OpenAPI, Panache/JPA, Flyway, REST Client, CI/CD, DevSecOps, event-driven design) that map directly to Spring ecosystems.
- **Deployment memory spikes on 1 GB RAM:** Pulling and unpacking container layers while old JVM containers are still running can trigger swap thrashing. Mitigated via serialized CD deployments (global `oci-deploy` concurrency queue) and stopping the old container prior to `docker pull`.
- **GraalVM native builds consume high RAM/CPU:** Mitigated by keeping GitHub Actions runners (7 GB RAM) responsible for all Docker builds (never building on the OCI VM) and defaulting to Fast-JAR until native compilation is explicitly needed for Phase 2 services.