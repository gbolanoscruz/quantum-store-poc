# ADR-003: Standardize Backend Microservices on Quarkus (Java 21 LTS)

- **Status:** Accepted
- **Date:** 2026-11-22
- **Deciders:** `@gbolanoscruz`

## Context

With the $0 hybrid infrastructure, DevSecOps guardrails, and automated GHCR/SSH deployment pipeline in place (ADR-001, ADR-002), `quantum-store-poc` is transitioning from the initial `hello-service` skeleton into real e-commerce microservices (Catalog, Orders, Inventory, Payments).

The backend framework must satisfy two core requirements:
1. **Low memory footprint & fast startup:** All backend containers run on a single Oracle Cloud Always Free `VM.Standard.E2.1.Micro` instance (1 OCPU, 1 GB RAM + 2 GB swap).
2. **Modern cloud-native Java ecosystem:** First-class support for containers, REST APIs, OpenAPI, health checks, observability, and eventual native compilation (GraalVM) without sacrificing developer productivity.

## Decision

Standardize 100% of backend microservices on **Quarkus** running on **Java 21 LTS** (Maven):

- **Runtime Strategy:** Start with **Quarkus Fast-JAR (JVM mode)** on Alpine JRE (`eclipse-temurin:21-jre-alpine`) with explicit container memory limits (`-XX:MaxRAMPercentage`), keeping the door open to **GraalVM native executables** for services that need sub-50 MB footprints.
- **Core Extensions:**
  - `quarkus-rest-jackson` (reactive/virtual-thread-friendly REST + JSON)
  - `quarkus-smallrye-health` (liveness/readiness probes for Docker & Caddy)
  - `quarkus-smallrye-openapi` (auto-generated Swagger/OpenAPI contracts)
  - `quarkus-hibernate-orm-panache` + `quarkus-jdbc-postgresql` (concise persistence layer)
- **Data & Messaging Offloading:** To preserve OCI RAM for microservices, stateful infrastructure (PostgreSQL, message brokers) will be offloaded to permanent $0 serverless tiers (e.g., Neon/Supabase for PostgreSQL, Upstash/CloudAMQP for messaging) rather than running heavy databases inside the 1 GB VM.

## Alternatives Considered

| Option | Pros | Reason Rejected |
| :--- | :--- | :--- |
| **Spring Boot (3.x / 4.x)** | Dominant enterprise adoption and ATS keyword recognition | Higher baseline JVM memory consumption (~200–350 MB per service), making it difficult to co-locate 3–4 microservices on a 1 GB RAM VM without heavy swapping. |
| **Hybrid (Spring Boot + Quarkus)** | Demonstrates multi-framework versatility | Doubles maintenance overhead (different BOMs, CI templates, observability/security configs) for a solo PoC. |
| **Raw JDK (`HttpServer`)** | Minimal footprint (used in `hello-service`) | Lacks built-in DI, ORM, OpenAPI, validation, and health probes needed for production-grade microservices. |

## Consequences

### Positive
- **High service density on 1 GB RAM:** Quarkus services typically run in ~60–120 MB in JVM mode (and ~30–50 MB native), enabling multiple services to coexist smoothly behind Caddy.
- **Container-first developer experience:** Fast builds, Dev Services for local testing, and clean multi-stage Dockerfiles.
- **Strong architectural differentiation:** Demonstrates pragmatic, constraint-driven engineering (*right tool for the job*) rather than defaulting to a framework blindly.

### Negative & Mitigations
- **Smaller job-market share than Spring Boot:** Mitigated by applying transferable enterprise patterns (Hexagonal/Clean Architecture, REST/OpenAPI, Panache/JPA, CI/CD, DevSecOps, event-driven design) that map directly to Spring ecosystems.
- **GraalVM native builds consume high RAM/CPU:** Mitigated by keeping GitHub Actions runners (7 GB RAM) responsible for all Docker builds (never building on the OCI VM) and defaulting to Fast-JAR until native mode is explicitly required.