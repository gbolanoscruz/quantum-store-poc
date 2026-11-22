# Security Policy

## Scope & Supported Versions

`quantum-store-poc` is an active Proof of Concept focused on cloud-native architecture and DevSecOps practices. Security updates and patches are applied exclusively to the latest state of the `main` branch.

| Branch / Version | Supported |
| :--- | :--- |
| `main` (latest) | :white_check_mark: |
| Feature branches / older commits | :x: |

---

## Active DevSecOps Controls

This repository enforces defense-in-depth guardrails across code, CI/CD, and runtime infrastructure:

- **Branch Protection (`main`):** GitHub Rulesets block direct pushes and require Pull Requests with passing status checks before merging.
- **Container & Filesystem Scanning:** **Trivy** runs automatically on every Pull Request as a required status check to catch known CVEs and misconfigurations.
- **Static Analysis (SAST):** **CodeQL** continuously analyzes Java code and GitHub Actions workflows.
- **Dependency Management:** **Dependabot** monitors dependencies and opens automated security/version update PRs.
- **Network Hardening:** Only ports `80` (ACME TLS validation) and `443` (HTTPS via Caddy) are exposed publicly on Oracle Cloud Security Lists. Application ports (e.g., `8080`) remain internal behind the reverse proxy.
- **Secret Hygiene:** SSH private keys, server IPs, DNS tokens, and credentials are never committed to version control.

---

## Reporting a Vulnerability

If you discover a security vulnerability in this repository or its live PoC endpoints, **please do not open a public GitHub Issue**.

Instead, report it privately using one of the following methods:

1. **GitHub Private Vulnerability Reporting (Preferred):** Go to the **Security** tab of this repository → **Advisories** → **Report a vulnerability**.
2. **Direct Contact:** Reach out to the repository maintainer (`@gbolanoscruz`) via GitHub with a brief description of the issue and steps to reproduce.

### What to Expect
- **Acknowledgment:** Within 72 hours of your report.
- **Remediation:** Validated vulnerabilities will be patched on a dedicated branch and merged into `main` via the standard security-checked PR workflow.