# Contributing to Quantum Store PoC

Thank you for contributing to `quantum-store-poc`. This repository follows a **Docs as Code** and **DevSecOps-first** workflow to keep `main` clean, secure, and always deployable.

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
   - `fix/<short-description>` — Bug fixes
   - `docs/<short-description>` — Documentation, runbooks, or ADRs
   - `ci/<short-description>` — GitHub Actions, security workflows, or tooling
   - `chore/<short-description>` — Maintenance, refactors, or dependency updates
3. Open a Pull Request targeting `main` and keep changes focused on a single concern.

---

## 2. Commit Message Convention

Use [Conventional Commits](https://www.conventionalcommits.org/) so the repository history stays readable and structured:

```text
<type>(<optional-scope>): <short imperative description>
```

**Examples:**
- `feat(frontend): point API_URL to fixed DuckDNS HTTPS endpoint`
- `docs: add SECURITY policy and initial ADRs`
- `fix(hello-service): handle CORS preflight requests`
- `ci(security): add Trivy container scan workflow`

---

## 3. Required Quality & Security Checks

Before a Pull Request can be merged into `main`:

- **Trivy Vulnerability Scanner** *(Required)* must pass with no blocking vulnerabilities or misconfigurations.
- **CodeQL Analysis** (Java / GitHub Actions) should pass without new security alerts.
- **Secret Hygiene:** Never commit SSH private keys (`.key`, `.pem`), real server IPs, DNS tokens, or credentials. Use `<PLACEHOLDER>` values in documentation and environment variables in code.

---

## 4. Documentation Expectations

- **Infrastructure changes:** Update `docs/infrastructure-runbook.md` when modifying OCI networking, firewall rules, Docker setup, or Cloudflare settings.
- **Architectural decisions:** Add a new ADR in `docs/adr/` (e.g., `003-microservices-stack.md`) whenever introducing a major framework, database, or architectural pattern.
- **Security issues:** Follow the private reporting process in [SECURITY.md](SECURITY.md) rather than opening a public issue.