# Contributing to Quantum Store PoC

Thank you for contributing to `quantum-store-poc`. This repository follows a **Docs as Code** and **DevSecOps-first** workflow to keep `main` clean, secure, and always deployable on our $0 hybrid cloud stack.

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
   - `docs/<short-description>` — Documentation, runbooks, or ADRs
   - `ci/<short-description>` — GitHub Actions, security workflows, or tooling
   - `chore/<short-description>` — Maintenance, refactors, or decommissioning
3. Open a Pull Request targeting `main` and keep changes focused on a single concern.

---

## 2. Commit Message Convention

Use [Conventional Commits](https://www.conventionalcommits.org/) so the repository history stays readable and structured:

```text
<type>(<optional-scope>): <short imperative description>
```

**Examples:**
- `feat(inventory): add Quarkus inventory-service with Neon DB and Catalog REST Client`
- `fix(docker): upgrade Alpine packages in runtime image to patch zlib CVEs`
- `ci(cd): serialize OCI deployments with global concurrency queue`
- `docs: update README and CONTRIBUTING for Phase 1 microservices`

---

## 3. Microservice Standards

All backend services live under `services/<service-name>/` and must adhere to the architecture defined in [ADR-003](docs/adr/003-microservices-stack-quarkus.md):

- **Runtime & Framework:** **Quarkus LTS** on **Java 25 LTS** (Maven) with `quarkus-smallrye-health` (`/q/health/ready`) and `quarkus-smallrye-openapi`.
- **Database-per-Service:** Each stateful service uses its own isolated Neon PostgreSQL database (`<service>_db`), managed strictly via **Flyway** migrations (`src/main/resources/db/migration/V*__*.sql`). Never share tables or foreign keys across services.
- **Dynamic Port Binding:** Bind HTTP to `quarkus.http.port=${PORT:808x}` for Cloud Run compatibility.
- **Inter-Service Communication:** Use **Quarkus REST Client** with upstream Cloud Run HTTPS URLs injected via environment variables at deploy time (e.g., `CATALOG_SERVICE_URL`).
- **Hardened Containers:** Use multi-stage Alpine Dockerfiles (`eclipse-temurin:25-jre-alpine`), run `apk update && apk upgrade --no-cache`, and execute as a non-root `quarkus` user.
- **CI/CD & Keyless Auth:**
  - Add a dedicated `.github/workflows/deploy-<service>.yml` workflow using Trivy, **Workload Identity Federation (OIDC)** (`permissions: id-token: write`), Artifact Registry, and `gcloud run deploy` (`--memory=512Mi`, `--cpu-boost`, `--min-instances=0`, `--max-instances=2`).
  - Register the new `pom.xml` in `.github/workflows/security-scan.yml` for Maven dependency resolution. *(Dependabot automatically monitors `/services/*` for Maven and Docker).*

---

## 4. Required Quality & Security Checks

Before a Pull Request can be merged into `main`:

- **Trivy Vulnerability Scanner** *(Required)* must pass with no blocking `CRITICAL` or `HIGH` vulnerabilities (both filesystem scans on PRs and container image scans before pushing to GHCR).
- **CodeQL Analysis** (Java / GitHub Actions) must pass without new security alerts.
- **Secret Hygiene:** Never commit SSH private keys (`.key`, `.pem`), Neon database URLs/passwords, real server IPs, or DNS tokens. Use `<PLACEHOLDER>` values in documentation and GitHub Secrets / environment variables in workflows and `application.properties`.

---

## 5. Documentation Expectations

- **Infrastructure changes:** Update [docs/infrastructure-runbook.md](docs/infrastructure-runbook.md) when modifying OCI networking, Caddy routes, firewall rules, swap/kernel tuning, or Cloudflare settings.
- **Architectural decisions:** Add a new ADR in `docs/adr/` (e.g., `004-event-driven-messaging.md`) whenever introducing a major framework, broker, database, or architectural pattern.
- **Security issues:** Follow the private reporting process in [SECURITY.md](SECURITY.md) rather than opening a public issue.