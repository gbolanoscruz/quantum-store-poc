# Infrastructure Runbook — Quantum Store PoC

This runbook documents the end-to-end provisioning, OS tuning, security hardening, networking, and deployment procedures for the **$0 hybrid infrastructure** (Cloudflare Workers + Oracle Cloud Always Free + Neon Serverless PostgreSQL).

> **Security Note:** Never commit SSH private keys, Neon credentials, real server IPs, or DNS tokens to this repository. Replace all `<PLACEHOLDER>` values locally or inject them via GitHub Actions Secrets.

---

## 1. Oracle Cloud (OCI) Networking & Compute

### 1.1 Virtual Cloud Network (VCN)
To ensure public IPv4 assignment and internet routing work out of the box, create the VCN **before** launching the VM:

1. In OCI Console, navigate to **Networking → Virtual Cloud Networks**.
2. Click **Start VCN Wizard → Create VCN with Internet Connectivity**.
3. Name it `quantum-store-vcn`, keep defaults, and click **Create**.
4. Open the created **Public Subnet → Default Security List → Ingress Rules** and add:

| Source CIDR | IP Protocol | Source Port Range | Destination Port Range | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| `0.0.0.0/0` | TCP | All *(empty)* | `80` | Let's Encrypt ACME HTTP-01 challenge |
| `0.0.0.0/0` | TCP | All *(empty)* | `443` | Public HTTPS traffic (Caddy) |

*Keep microservice ports (`8081`, `8082`, etc.) closed to the public internet and bound to `127.0.0.1` so all external traffic flows strictly through Caddy over TLS.*

### 1.2 Compute Instance
- **Shape:** `VM.Standard.E2.1.Micro` *(Always Free — AMD x86_64, 1 OCPU, 1 GB RAM)*.
  *(Note: `VM.Standard.A1.Flex` ARM instances are also Always Free but frequently hit `Out of capacity` errors on Free Tier accounts).*
- **OS Image:** Oracle Linux 9 (`x86_64`).
- **Networking:** Select the existing `quantum-store-vcn` and its **public subnet**, then enable **Automatically assign public IPv4 address**.
- **SSH Key:** Download the generated private key and store it strictly **outside** this repository (`~/.ssh/quantum-store.key`).

---

## 2. Local SSH Setup

### Linux / macOS / Git Bash
```bash
mkdir -p ~/.ssh
mv ~/Downloads/<DOWNLOADED_KEY>.key ~/.ssh/quantum-store.key
chmod 700 ~/.ssh
chmod 600 ~/.ssh/quantum-store.key
ssh -i ~/.ssh/quantum-store.key opc@<OCI_PUBLIC_IP>
```

### Windows PowerShell
PowerShell does not support `chmod`; use `icacls` to restrict key permissions:

```powershell
New-Item -ItemType Directory -Force -Path "$env:USERPROFILE\.ssh"
Move-Item "$env:USERPROFILE\Downloads\<DOWNLOADED_KEY>.key" "$env:USERPROFILE\.ssh\quantum-store.key"
icacls "$env:USERPROFILE\.ssh\quantum-store.key" /inheritance:r
icacls "$env:USERPROFILE\.ssh\quantum-store.key" /grant:r "$($env:USERNAME):R"
ssh -i "$env:USERPROFILE\.ssh\quantum-store.key" opc@<OCI_PUBLIC_IP>
```

### Optional SSH Alias (`~/.ssh/config`)
```text
Host quantum-vm
    HostName <OCI_PUBLIC_IP>
    User opc
    IdentityFile ~/.ssh/quantum-store.key
```

---

## 3. Oracle Linux 9 Tuning & Base Tooling

### 3.1 Verify or Configure Swap (Critical on 1 GB RAM)
Without at least 2 GB of swap, `dnf` metadata parsing and multiple JVM containers trigger the Linux OOM Killer (`Killed`). Check existing swap first with `free -h`; if none is active, create a 2 GB swapfile:

```bash
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h
```

### 3.2 Kernel Stability & Auto-Recovery on OOM
Lower `swappiness` to avoid premature disk thrashing and force an automatic reboot after 10 seconds if the kernel hits an unrecoverable Out-Of-Memory state (instead of freezing SSH indefinitely):

```bash
sudo tee /etc/sysctl.d/99-quantum-stability.conf > /dev/null << 'EOF'
vm.swappiness=10
vm.panic_on_oom=1
kernel.panic=10
EOF
sudo sysctl --system
```

### 3.3 Install Docker & Git
Disable the massive `ksplice` and `oci` metadata repositories during installation to avoid multi-minute hangs on low-RAM instances:

```bash
sudo dnf install -y --disablerepo="*ksplice*" --disablerepo="*oci*" git dnf-utils
sudo dnf config-manager --add-repo [https://download.docker.com/linux/centos/docker-ce.repo](https://download.docker.com/linux/centos/docker-ce.repo)
sudo dnf install -y --disablerepo="*ksplice*" --disablerepo="*oci*" \
  docker-ce docker-ce-cli containerd.io docker-compose-plugin

sudo systemctl enable --now docker
sudo usermod -aG docker $USER
newgrp docker
docker --version
```

### 3.4 Configure `firewalld`
Open HTTP and HTTPS and **restart Docker afterward** (on Oracle Linux 9, `firewall-cmd --reload` flushes Docker's `iptables` rules):

```bash
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --reload
sudo systemctl restart docker
```

---

## 4. Microservices Runtime, Networking & Databases

### 4.1 Internal Docker Network (`quantum-net`)
Microservices communicate over a private Docker bridge network by container name (e.g., `http://catalog-service:8081`) without traversing the public internet or Caddy:

```bash
docker network create quantum-net
```

### 4.2 Externalized Databases (Neon Serverless PostgreSQL)
To preserve OCI RAM exclusively for Quarkus runtimes, PostgreSQL is hosted on **Neon Free Tier** following the **Database-per-Service** pattern inside a single `quantum-store` project:

| Microservice | Container Port | Neon Database | GitHub Secrets |
| :--- | :--- | :--- | :--- |
| `catalog-service` | `127.0.0.1:8081` | `catalog_db` | `CATALOG_DB_URL`, `CATALOG_DB_USER`, `CATALOG_DB_PASSWORD` |
| `inventory-service` | `127.0.0.1:8082` | `inventory_db` | `INVENTORY_DB_URL`, `INVENTORY_DB_USER`, `INVENTORY_DB_PASSWORD` |

Schema migrations and seed data are managed automatically at startup by **Flyway**.

### 4.3 Automated Deployment Pattern (GitHub Actions → GHCR → OCI)
Docker builds and Trivy image scans run exclusively on GitHub Actions runners (7 GB RAM) and push scanned images to GHCR (`ghcr.io/gbolanoscruz/quantum-store-poc/<service>:latest`).

To protect the 1 GB RAM VM during deployments, every `deploy-*.yml` workflow enforces:
- **Global Concurrency Queue:** `group: oci-deploy` (`cancel-in-progress: false`, `queue: max`) on the `deploy-oci` job so services never deploy over SSH simultaneously.
- **Stop-Before-Pull:** Stops and removes the old container *before* running `docker pull` to free ~180 MiB of RAM for layer decompression.
- **Explicit Memory Caps:** `--memory="256m"` per Quarkus container (`-XX:MaxRAMPercentage=75.0`).
- **Cold-Start Healthcheck Loop:** Retries `http://localhost:<port>/q/health/ready` up to 60 seconds (12 × 5s) to accommodate Neon wake-up and Flyway startup.

---

## 5. Fixed HTTPS Endpoint (DuckDNS + Caddy)

Rather than using ephemeral `trycloudflare.com` quick tunnels, we use **DuckDNS** for a fixed free subdomain and **Caddy** as an API Gateway / reverse proxy with automatic Let's Encrypt TLS.

### 5.1 DuckDNS Setup
1. Sign in at [duckdns.org](https://www.duckdns.org/) and register `<YOUR_SUBDOMAIN>.duckdns.org`.
2. Point **current ip** to `<OCI_PUBLIC_IP>` and click **update ip**.
3. Verify DNS propagation: `nslookup <YOUR_SUBDOMAIN>.duckdns.org`.

### 5.2 Run & Reload Caddy Reverse Proxy
```bash
mkdir -p ~/caddy
cat << 'EOF' > ~/caddy/Caddyfile
<YOUR_SUBDOMAIN>.duckdns.org {
    handle /api/catalog* {
        reverse_proxy localhost:8081
    }
    handle /api/inventory* {
        reverse_proxy localhost:8082
    }
    handle {
        respond "Quantum Store API — route not found" 404
    }
}
EOF

docker run -d --name caddy \
  --network host \
  --restart unless-stopped \
  --memory="64m" \
  -v ~/caddy/Caddyfile:/etc/caddy/Caddyfile \
  -v caddy_data:/data \
  -v caddy_config:/config \
  caddy:alpine
```

To apply route changes without downtime:
```bash
docker exec caddy caddy fmt --overwrite /etc/caddy/Caddyfile
docker exec caddy caddy reload --config /etc/caddy/Caddyfile
```

Verify certificate issuance (wait ~15 seconds on first boot):
```bash
docker logs caddy 2>&1 | grep -i "certificate"
```

---

## 6. Lightweight Observability & Self-Healing

### 6.1 Container Readiness Watchdog (Cron)
To auto-restart a JVM container if it becomes unresponsive without consuming extra RAM on monitoring agents, add a 5-minute `cron` check on OCI:

```bash
(crontab -l 2>/dev/null; \
  echo "*/5 * * * * curl -fsS --max-time 10 http://localhost:8081/q/health/ready >/dev/null || docker restart catalog-service"; \
  echo "*/5 * * * * curl -fsS --max-time 10 http://localhost:8082/q/health/ready >/dev/null || docker restart inventory-service" \
) | crontab -
```

### 6.2 External Uptime Monitoring ($0)
Configure a 5-minute HTTP monitor in **UptimeRobot** or **Better Stack** targeting `https://<YOUR_SUBDOMAIN>.duckdns.org/api/catalog/products` to receive alerts on TLS, Caddy, OCI, or Neon outages.

---

## 7. Frontend Deployment (Cloudflare Workers Static Assets)

- **Connected Repo:** `gbolanoscruz/quantum-store-poc` (`main` branch).
- **Assets Directory:** `frontend/`.
- **Production URL:** `https://quantum-store-poc.gbolanoscruz.workers.dev`.
- **Non-Production PR Builds:** By default, Cloudflare Workers Builds runs `npx wrangler versions upload` on pull requests. Either disable builds for non-production branches in **Cloudflare Dashboard → Settings → Build**, or add a root `wrangler.jsonc` declaring `"assets": { "directory": "./frontend" }`.

---

## 8. Quick Troubleshooting Reference

| Symptom | Root Cause | Fix |
| :--- | :--- | :--- |
| `dnf` exits with `Killed` | Out of memory on 1 GB RAM VM | Ensure 2 GB swapfile is active (Section 3.1) and run `sudo dnf clean all` |
| `dnf install` hangs for 10+ min | Decompressing `ksplice` / `oci` repo metadata on swap | Pass `--disablerepo="*ksplice*" --disablerepo="*oci*"` |
| VM freezes / SSH hangs indefinitely | Heavy swap thrashing or OOM without kernel panic reboot | Force reboot in OCI Console and apply `99-quantum-stability.conf` (Section 3.2) |
| GitHub Actions SSH deploy hits `Run Command Timeout` | Concurrent deploys or `docker pull` competing with running JVM containers for RAM | Use `concurrency: oci-deploy` and stop old container before `docker pull` (Section 4.3) |
| Healthcheck fails right after `docker run` | Cold start (Neon wake-up + Flyway) takes >10s on 1 OCPU | Use a 60s retry loop against `/q/health/ready` |
| Trivy blocks Docker push on Alpine OS CVEs (e.g., `zlib`) | Base image `eclipse-temurin:21-jre-alpine` ships unpatched OS packages | Run `apk update && apk upgrade --no-cache` in Stage 2 of the `Dockerfile` |
| Browser times out after `firewall-cmd --reload` | Reloading `firewalld` wipes Docker's NAT/`iptables` rules | Run `sudo systemctl restart docker` |
| SSH fails with `UNPROTECTED PRIVATE KEY FILE` on Windows | Windows ACLs inherit broad permissions | Run `icacls` with `/inheritance:r` and `/grant:r "$($env:USERNAME):R"` |
| Frontend shows `NetworkError` on `fetch` | Mixed content (HTTP vs HTTPS), CORS mismatch, or 502 from Caddy | Confirm Caddy TLS is active, container is `Up`, and `quarkus.http.cors.origins` includes the Workers URL |