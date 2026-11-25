# Contributing to Quantum Store PoC

Thank you for contributing to `quantum-store-poc`. This repository follows a **Docs as Code** and **DevSecOps-first** workflow to keep `main` clean, tested, secure, and always deployable on our $0 serverless cloud stack.

---

## 1. Branching & Pull Request Workflow

Direct pushes to `main` are blocked by GitHub Rulesets. All changes must go through a Pull Request:

1. Sync your local `main` branch:
   ```bash
   git checkout main
   git pull origin main
   ```
2. Create a descriptive branch from `main` using one of these prefixes:
   - `feat/<short-description>` — New features or microservices
   - `fix/<short-description>` — Bug fixes or CVE patches
   - `test/<short-description>` — Unit/integration tests or CI testing updates
   - `docs/<short-description>` — Documentation, runbooks, or ADRs
   - `ci/<short-description>` — GitHub Actions, security workflows, or tooling
   - `refactor/<short-description>` — Code structure improvements without behavior changes
3. Open a Pull Request targeting `main` and keep changes focused on a single concern.

---

## 2. Commit Message Convention

Use [Conventional Commits](https://www.conventionalcommits.org/) so the repository history stays readable and structured:

```text
<type>(<optional-scope>): <short imperative description>
```

**Examples:**
- `feat(payments): add Quarkus Java 25 payments-service with cascading refund`
- `test(ci): add root Maven aggregator and Quarkus Dev Services test suites`
- `feat(observability): add GCP JSON logging, OpenTelemetry, and Prometheus metrics`
- `docs: update README, CONTRIBUTING, and infrastructure runbook`

---

## 3. Microservice Standards

All backend services live under `services/<service-name>/` and must adhere to [ADR-003](docs/adr/003-microservices-stack-quarkus.md) and [ADR-004](docs/adr/004-migrate-backend-to-cloud-run.md):

- **Runtime & Framework:** **Quarkus 3.40 LTS** on **Java 25 LTS** (Maven) with `quarkus-smallrye-health` (`/q/health/ready`) and `quarkus-smallrye-openapi`.
- **Database-per-Service:** Each stateful service uses its own isolated Neon PostgreSQL database (`<service>_db`), managed via **Flyway** migrations (`src/main/resources/db/migration/V*__*.sql`). Keep production JDBC settings scoped to `%prod.` in `application.properties` so Quarkus Dev Services can manage local test databases when running `mvn test`.
- **Inter-Service Communication:** Use **Quarkus REST Client** with upstream Cloud Run URLs injected via environment variables at deploy time.
- **Observability:** Include `quarkus-logging-json` (`gcp` format in prod), `quarkus-opentelemetry` (context propagation), and `quarkus-micrometer-registry-prometheus` (`/q/metrics`). Avoid manual version overrides on internal `io.opentelemetry.*` artifacts that conflict with the Quarkus BOM.
- **Hardened Containers:** Use multi-stage Alpine Dockerfiles (`eclipse-temurin:25-jre-alpine`), run `apk update && apk upgrade --no-cache`, execute as a non-root `quarkus` user, and set `-XX:MaxRAMPercentage=75.0`.
- **CI/CD & Registration:**
  - Add the new module to the root `pom.xml` and `.github/workflows/ci-tests.yml` matrix.
  - Add a dedicated `.github/workflows/deploy-<service>.yml` using GCP **Workload Identity Federation (OIDC)** and Trivy image scanning before pushing to Artifact Registry.

---

## 4. Required Quality & Security Checks

Before a Pull Request can be merged into `main`:

- **CI — Microservices Tests** *(Required)*: Matrix jobs (`mvn -B test`) must pass.
- **Trivy Vulnerability Scanner** *(Required)*: Must pass with no blocking `CRITICAL` or `HIGH` vulnerabilities (filesystem scan on PRs and container image scan before deploy).
- **CodeQL Analysis** (Java / GitHub Actions): Must pass without new security alerts.
- **Secret Hygiene:** Never commit Neon database URLs/passwords, GCP project IDs/keys, or tokens. Use `<PLACEHOLDER>` values in documentation and GitHub Secrets / environment variables in workflows.

---

## 5. Documentation Expectations

- **Infrastructure changes:** Update [docs/infrastructure-runbook.md](docs/infrastructure-runbook.md) when modifying Cloud Run settings, Artifact Registry policies, Neon databases, or observability endpoints.
- **Architectural decisions:** Add a new ADR in `docs/adr/` (starting from `005-*.md`) whenever introducing a major framework, broker, database, or architectural pattern.
- **Security issues:** Follow the private reporting process in [SECURITY.md](SECURITY.md) rather than opening a public issue.