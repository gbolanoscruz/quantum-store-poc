# ADR-002: Fixed HTTPS Endpoint with DuckDNS and Caddy Reverse Proxy

- **Status:** Accepted
- **Date:** 2026-11-22
- **Deciders:** `@gbolanoscruz`

## Context

The storefront is hosted on Cloudflare Workers over `https://`. Modern browsers block mixed-content requests from an HTTPS origin to an unencrypted HTTP backend (`http://<OCI_PUBLIC_IP>:8080`), requiring a valid TLS certificate for the Oracle Cloud API endpoint.

During initial validation, we used an ephemeral **Cloudflare Quick Tunnel** (`trycloudflare.com`). However, Quick Tunnels generate a new random subdomain on every container or Docker daemon restart, immediately breaking the frontend's `API_URL` (`NetworkError`) and requiring a code change and redeployment to recover.

We need a **permanent, $0 HTTPS endpoint** that survives restarts and can scale to route multiple microservices behind a single domain.

## Decision

Replace ephemeral quick tunnels with **DuckDNS + Caddy** running in Docker on the Oracle Cloud VM:

- **Fixed DNS:** Register a free static subdomain on **DuckDNS** (`<YOUR_SUBDOMAIN>.duckdns.org`) pointing to the OCI instance's public IPv4 address.
- **Reverse Proxy & Auto-TLS:** Run **Caddy (`caddy:alpine`)** with `--network host` and persistent Docker volumes (`caddy_data`, `caddy_config`) to automatically provision and renew Let's Encrypt certificates via ACME HTTP-01.
- **Least-Privilege Networking:** Expose only ports `80` (ACME validation / HTTP→HTTPS redirect) and `443` (HTTPS) in OCI Security Lists and `firewalld`, keeping internal microservice ports (such as `8080`) closed to the public internet.

## Alternatives Considered

| Option | Pros | Reason Rejected |
| :--- | :--- | :--- |
| **Cloudflare Quick Tunnels (`trycloudflare.com`)** | Zero firewall ports required; instant setup | Ephemeral URLs change on every container/VM restart, breaking frontend connectivity. |
| **Cloudflare Named Tunnels (Zero Trust)** | Fixed URL with zero open inbound ports on OCI | Requires purchasing/owning a custom domain delegated to Cloudflare DNS, breaking the $0 constraint for now. |
| **Nginx + Certbot** | Industry-standard reverse proxy | Requires extra configuration and renewal automation compared to Caddy's single-file `Caddyfile` and native ACME support. |

## Consequences

### Positive
- **Stable HTTPS URL** that persists across Docker and VM reboots without editing frontend configuration.
- **Zero-maintenance TLS:** Automatic Let's Encrypt issuance and renewal via Caddy.
- **Gateway-ready:** The `Caddyfile` can easily route future microservices by path (e.g., `/api/catalog/*`, `/api/orders/*`) under the same domain.
- **Reduced attack surface:** Application containers stay private behind Caddy instead of exposing raw ports.

### Negative & Mitigations
- **Public exposure of ports `80` and `443`:** Unlike a Cloudflare Named Tunnel, the VM's public IP is reachable on standard web ports. Mitigated by keeping all backend ports closed at both OCI VCN and `firewalld` levels, and reserving migration to a Cloudflare Named Tunnel once a custom domain is added.
- **Oracle Linux 9 `firewalld` + Docker interaction:** Running `firewall-cmd --reload` flushes Docker's `iptables` rules. Mitigated by documenting `sudo systemctl restart docker` as a mandatory post-reload step in `docs/infrastructure-runbook.md`.