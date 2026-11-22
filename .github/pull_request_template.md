## Summary

<!-- Briefly describe what this PR changes and why. -->

## Type of Change

- [ ] `feat`: New feature or microservice
- [ ] `fix`: Bug fix
- [ ] `docs`: Documentation, runbook, or ADR update
- [ ] `ci`: GitHub Actions, security workflows, or tooling
- [ ] `chore`: Maintenance, refactor, or dependency update

## Related Issues / ADRs

<!-- Link related issues or ADRs (e.g., Closes #12, Refs ADR-002). Leave N/A if none. -->
- N/A

## Testing & Verification

<!-- Describe how you verified these changes (local Docker build, curl output, browser test, etc.). -->

- [ ] Tested locally
- [ ] Verified live endpoint / build (if applicable)

## DevSecOps & Documentation Checklist

- [ ] **Secret Hygiene:** No SSH keys (`.key`, `.pem`), real server IPs, DNS tokens, or credentials committed.
- [ ] **Security Checks:** Trivy Vulnerability Scanner and CodeQL pass without new blocking alerts.
- [ ] **Conventional Commits:** Branch name and commit messages follow `CONTRIBUTING.md`.
- [ ] **Docs as Code:** Updated `README.md`, `docs/infrastructure-runbook.md`, or added an ADR in `docs/adr/` if architecture/infrastructure changed.