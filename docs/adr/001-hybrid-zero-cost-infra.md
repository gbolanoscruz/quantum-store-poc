# ADR-001: Hybrid Zero-Cost Cloud Infrastructure (Cloudflare + Oracle Cloud Always Free)

- **Status:** Accepted
- **Date:** 2026-11-22
- **Deciders:** `@gbolanoscruz`

## Context

`quantum-store-poc` requires a publicly accessible, production-like environment to host a modern web storefront and Dockerized Java microservices while enforcing DevSecOps practices. The infrastructure must satisfy three constraints:

1. **Zero cost ($0/month):** No expiring 12-month trials or surprise cloud bills.
2. **Always-on backend:** Microservices must remain running without aggressive PaaS sleep policies or multi-second cold starts.
3. **Full infrastructure control:** Ability to manage Docker, reverse proxies, firewalls, and least-privilege networking directly.

## Decision

Adopt a **hybrid cloud architecture** splitting static delivery and backend compute across two permanent free tiers:

- **Frontend Hosting:** **Cloudflare Workers (Static Assets)** connected to the GitHub `main` branch for automated deployments, global edge caching, and managed HTTPS.
- **Backend Compute:** **Oracle Cloud Infrastructure (OCI) Always Free** using a `VM.Standard.E2.1.Micro` instance (AMD `x86_64`, 1 OCPU, 1 GB RAM) running **Oracle Linux 9** and **Docker**.
  - *Compute Shape Note:* `VM.Standard.A1.Flex` (ARM) offers higher specs under Always Free, but `VM.Standard.E2.1.Micro` was selected due to recurring `Out of capacity` limits on Free Tier accounts.
  - *OS Tuning:* A 2 GB swapfile is provisioned to prevent Linux OOM kills during package management (`dnf`) and Java container builds.

## Alternatives Considered

| Option | Pros | Reason Rejected |
| :--- | :--- | :--- |
| **PaaS Free Tiers** *(Render, Railway, Koyeb)* | Minimal ops overhead; built-in Git deploys | Spin-down / sleep policies on inactivity, monthly credit caps, or limited container networking control. |
| **AWS / Azure Free Tiers** | Industry-standard ecosystems | Free compute expires after 12 months, violating the permanent $0 goal. |
| **OCI `VM.Standard.A1.Flex` (ARM)** | Up to 4 OCPUs and 24 GB RAM Always Free | Unavailable in target region due to persistent Free Tier capacity exhaustion. |

## Consequences

### Positive
- **$0/month indefinite operation** with real VCN networking, firewall rules, and container orchestration.
- **Global edge performance** for the storefront via Cloudflare without consuming backend VM resources.
- **Hands-on DevSecOps realism:** Enforces real-world OS hardening, `firewalld` management, and container security scanning.

### Negative & Mitigations
- **Strict 1 GB RAM ceiling on backend VM:** Mitigated by configuring a 2 GB swapfile, disabling heavy OCI `dnf` metadata repos (`ksplice`, `oci`) during installs, preferring lightweight runtimes (e.g., Alpine JRE, Quarkus, or tuned JVM), and offloading databases/messaging to external serverless free tiers in future phases.
- **Manual OS & Docker maintenance:** Mitigated by documenting all provisioning and recovery steps in `docs/infrastructure-runbook.md`.