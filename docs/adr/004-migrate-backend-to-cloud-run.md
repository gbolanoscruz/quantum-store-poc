# ADR-004: Migrate Backend Microservices from OCI Micro VM to Google Cloud Run

- **Status:** Accepted
- **Date:** 2026-11-23
- **Deciders:** `@gbolanoscruz`
- **Supersedes:** [ADR-001](001-hybrid-zero-cost-infra.md) *(backend compute layer)*, [ADR-002](002-fixed-https-duckdns-caddy.md)

## Context

Under ADR-001 and ADR-002, backend microservices ran on a single Oracle Cloud Always Free `VM.Standard.E2.1.Micro` instance (1 OCPU, 1 GB RAM + 2 GB swap) behind a self-hosted Caddy reverse proxy and DuckDNS.

Once the project moved from a single `hello-service` skeleton to multiple stateful **Quarkus (Java 21)** microservices (`catalog-service` and `inventory-service` with Hibernate ORM, Flyway, and Neon JDBC), the 1 GB RAM VM became an operational bottleneck:

1. **Memory saturation & swap thrashing:** Two JVM containers (~180 MB each), Docker daemon overhead, and Caddy left minimal free RAM. Routine `docker pull` operations during CI/CD triggered severe swap thrashing, freezing SSH and causing `Run Command Timeout` failures in GitHub Actions.
2. **Unnecessary operational toil:** Maintaining OS swap, `sysctl` OOM panic rules, `firewalld`/`iptables` quirks, Caddyfile reloads, cron watchdogs, and serialized deployment queues (`oci-deploy`) consumed time without advancing core e-commerce features.
3. **Static SSH credentials:** Deploying via `appleboy/ssh-action` required storing a long-lived SSH private key in GitHub Secrets.
4. **Poor horizontal headroom:** Adding upcoming Phase 2 services (`orders-service`, `payments-service`) on the same 1 GB VM was not viable without degrading stability.

## Decision

Migrate all backend microservices to **Google Cloud Run** (`us-central1`, Tier 1 Always Free) and **Google Artifact Registry**, decommissioning the OCI VM, Caddy, and DuckDNS:

- **Serverless Container Runtime:** Each microservice deploys as an independent Cloud Run service with **512 MiB RAM**, **1 vCPU**, **Startup CPU Boost** (`--cpu-boost`), **`--min-instances=0`** (scale-to-zero for $0 cost), and **`--max-instances=2`** (cost guardrail).
- **Managed HTTPS & Routing:** Cloud Run provides native TLS endpoints (`*.run.app`) out of the box, eliminating Caddy and DuckDNS.
- **Dynamic Port Binding:** Quarkus services bind to `${PORT:808x}` to comply with the Cloud Run container contract while preserving local port defaults.
- **Keyless CI/CD (OIDC):** GitHub Actions authenticates to GCP using **Workload Identity Federation (WIF)** with least-privilege IAM roles (`roles/run.admin`, `roles/artifactregistry.writer`, `roles/iam.serviceAccountUser`) — zero static SSH keys or exported JSON service account keys.
- **Artifact Registry Hygiene:** A cleanup policy retains only the **latest 2 image versions per service** to remain strictly within the 0.5 GB Always Free storage tier.
- **Service Discovery:** `inventory-service` resolves the live HTTPS URL of `catalog-service` dynamically during CD (`gcloud run services describe`) and injects it via `CATALOG_SERVICE_URL` into the Quarkus REST Client.
- **Unchanged Components:** **Neon Serverless PostgreSQL** (*Database-per-Service*), **Cloudflare Workers** (static storefront), **Quarkus 3.40 LTS (Java 21)**, and **Trivy/CodeQL/Dependabot** remain unchanged.

## Alternatives Considered

| Option | Pros | Reason Rejected |
| :--- | :--- | :--- |
| **Keep OCI `VM.Standard.E2.1.Micro`** | Zero migration effort, 24/7 warm containers | Unstable under 2+ JVM services; frequent freezes and SSH deploy timeouts; cannot scale to 4+ microservices. |
| **OCI Ampere A1 (`VM.Standard.A1.Flex`) via PAYG** | Up to 2 OCPUs / 12 GB RAM Always Free, no cold starts | Requires upgrading OCI account to Pay-As-You-Go, cross-compiling `linux/arm64` images, and continuing to manage OS, Docker, Caddy, and SSH keys manually. |
| **Render Free Web Services** | No credit card required | Aggressive 15-minute idle spin-down with ~50–60s cold starts, making synchronous service-to-service REST calls unreliable. |
| **Azure Container Apps** | Comparable serverless model with GHCR support | Slightly higher initial setup complexity than Cloud Run's single-command `gcloud run deploy` + WIF integration. |

## Consequences

### Positive
- **Zero-maintenance stability:** Eliminates VM freezes, OOM reboots, swap tuning, `firewalld`, Caddy, and deployment concurrency locks.
- **Isolated resources per service:** Each microservice gets 512 MiB RAM and 1 vCPU independently, unblocking Phase 2 (`orders-service`, `payments-service`).
- **Stronger DevSecOps posture:** Replaces static SSH keys with short-lived OIDC tokens via Workload Identity Federation.
- **$0 cost preserved:** Remains within Google Cloud Run Always Free limits (180k vCPU-s, 360k GiB-s, 2M requests/month) and Artifact Registry's 0.5 GB free tier, backed by a $1 USD billing budget alert.

### Negative & Mitigations
- **Cold starts on scale-to-zero (~2–4s):** Mitigated by enabling `--cpu-boost` on Cloud Run and Quarkus's fast JVM startup. Can be further reduced with GraalVM native images if needed.
- **Publicly reachable service-to-service endpoints (current PoC):** Currently both services use `--allow-unauthenticated` so the Cloudflare Workers frontend can call them directly. For future internal-only services, Cloud Run IAM invoker authentication or an API Gateway can be introduced.