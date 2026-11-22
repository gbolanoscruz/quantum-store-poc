# Infrastructure Runbook — Quantum Store PoC

This runbook documents the end-to-end provisioning, OS tuning, security hardening, and deployment procedures for the **$0 hybrid infrastructure** (Cloudflare Workers + Oracle Cloud Always Free).

> **Security Note:** Never commit SSH private keys, real server IPs, or DNS tokens to this repository. Replace all `<PLACEHOLDER>` values locally when executing these commands.

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

*Keep port `8080` closed to the public internet once Caddy is active so all traffic flows through TLS.*

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

### 3.1 Configure 2 GB Swap (Critical on 1 GB RAM)
Without swap, `dnf` metadata parsing and Java Docker builds trigger the Linux OOM Killer (`Killed`):

```bash
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h
```

### 3.2 Install Docker & Git
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

### 3.3 Configure `firewalld`
Open HTTP and HTTPS and **restart Docker afterward** (on Oracle Linux 9, `firewall-cmd --reload` flushes Docker's `iptables` rules):

```bash
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --reload
sudo systemctl restart docker
```

---

## 4. Deploy Backend Service (`hello-service`)

```bash
git clone [https://github.com/gbolanoscruz/quantum-store-poc.git](https://github.com/gbolanoscruz/quantum-store-poc.git)
cd quantum-store-poc/services/hello-service
docker build -t hello-service .
docker run -d -p 8080:8080 --name hello-service --restart unless-stopped hello-service
```

Verify locally on the VM:
```bash
curl -i http://localhost:8080/api/hello
```

---

## 5. Fixed HTTPS Endpoint (DuckDNS + Caddy)

Rather than using ephemeral `trycloudflare.com` quick tunnels (which regenerate their URL on every container restart), we use **DuckDNS** for a fixed free subdomain and **Caddy** for automatic Let's Encrypt TLS certificates.

### 5.1 DuckDNS Setup
1. Sign in at [duckdns.org](https://www.duckdns.org/) and register `<YOUR_SUBDOMAIN>.duckdns.org`.
2. Point **current ip** to `<OCI_PUBLIC_IP>` and click **update ip**.
3. Verify DNS propagation: `nslookup <YOUR_SUBDOMAIN>.duckdns.org`.

### 5.2 Run Caddy Reverse Proxy
```bash
mkdir -p ~/caddy
cat << 'EOF' > ~/caddy/Caddyfile
<YOUR_SUBDOMAIN>.duckdns.org {
    reverse_proxy localhost:8080
}
EOF

docker run -d --name caddy \
  --network host \
  --restart unless-stopped \
  -v ~/caddy/Caddyfile:/etc/caddy/Caddyfile \
  -v caddy_data:/data \
  -v caddy_config:/config \
  caddy:alpine
```

Verify certificate issuance (wait ~15 seconds):
```bash
docker logs caddy 2>&1 | grep -i "certificate"
curl -i https://<YOUR_SUBDOMAIN>.duckdns.org/api/hello
```

---

## 6. Frontend Deployment (Cloudflare Workers Static Assets)

- **Connected Repo:** `gbolanoscruz/quantum-store-poc` (`main` branch).
- **Assets Directory:** `frontend/`.
- **Production URL:** `https://quantum-store-poc.gbolanoscruz.workers.dev`.
- **Non-Production PR Builds:** By default, Cloudflare Workers Builds runs `npx wrangler versions upload` on pull requests. Either disable builds for non-production branches in **Cloudflare Dashboard → Settings → Build**, or add a root `wrangler.jsonc` declaring `"assets": { "directory": "./frontend" }`.

---

## 7. Quick Troubleshooting Reference

| Symptom | Root Cause | Fix |
| :--- | :--- | :--- |
| `dnf` exits with `Killed` | Out of memory on 1 GB RAM VM | Add 2 GB swapfile (Section 3.1) and run `sudo dnf clean all` |
| `dnf install` hangs for 10+ min | Decompressing `ksplice` / `oci` repo metadata on swap | Pass `--disablerepo="*ksplice*" --disablerepo="*oci*"` |
| Browser times out after `firewall-cmd --reload` | Reloading `firewalld` wipes Docker's NAT/`iptables` rules | Run `sudo systemctl restart docker` |
| SSH fails with `UNPROTECTED PRIVATE KEY FILE` on Windows | Windows ACLs inherit broad permissions | Run `icacls` with `/inheritance:r` and `/grant:r "$($env:USERNAME):R"` |
| Frontend shows `NetworkError` on `fetch` | Mixed content (HTTP vs HTTPS), expired tunnel URL, or CORS/502 error | Confirm Caddy certificate is active and `API_URL` uses `https://` without a trailing slash |