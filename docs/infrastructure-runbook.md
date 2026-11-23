# Infrastructure Runbook — Quantum Store PoC

This runbook documents the provisioning, keyless CI/CD authentication (OIDC), container registry hygiene, and troubleshooting procedures for the **$0 serverless infrastructure** (Cloudflare Workers + Google Cloud Run + Neon Serverless PostgreSQL).

> **Security Note:** Never commit GCP credentials, Neon database URLs/passwords, or tokens to this repository. All secrets are injected via GitHub Actions Secrets, and GCP authentication uses short-lived OIDC tokens.

---

## 1. Google Cloud Project & Cost Guardrails

1. Create a GCP project (e.g., `quantum-store-poc`) and link a billing account.
2. In **Billing → Budgets & alerts**, create a **$1 USD monthly budget alert** (50% and 100% thresholds) to guarantee early warning if usage ever approaches Always Free limits.
3. Always deploy in a **Tier 1 region** (`us-central1`) to qualify for Google Cloud Run's Always Free allowance (180,000 vCPU-s, 360,000 GiB-s, and 2M requests/month).

---

## 2. Artifact Registry & Workload Identity Federation (Cloud Shell)

Run the following one-time setup in **Google Cloud Shell** to provision Artifact Registry, a 2-version cleanup policy (keeping storage under the 0.5 GB free tier), a least-privilege Service Account, and GitHub OIDC federation:

```bash
export PROJECT_ID="<GCP_PROJECT_ID>"
export PROJECT_NUMBER="<GCP_PROJECT_NUMBER>"
export REGION="us-central1"
export REPO="gbolanoscruz/quantum-store-poc"
export SA_EMAIL="github-deployer@${PROJECT_ID}.iam.gserviceaccount.com"

gcloud config set project "$PROJECT_ID"

# 1. Enable required APIs
gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  iamcredentials.googleapis.com

# 2. Create Docker repository in Artifact Registry
gcloud artifacts repositories create quantum-store \
  --repository-format=docker \
  --location="$REGION" \
  --description="Quantum Store microservices images"

# 3. Enforce cleanup policy (retain latest 2 versions per image to stay < 0.5 GB)
cat << 'EOF' > cleanup.json
[
  {
    "name": "delete-old",
    "action": {"type": "Delete"},
    "condition": {"olderThan": "0s"}
  },
  {
    "name": "keep-latest-2",
    "action": {"type": "Keep"},
    "mostRecentVersions": {"keepCount": 2}
  }
]
EOF
gcloud artifacts repositories set-cleanup-policies quantum-store \
  --location="$REGION" \
  --policy=cleanup.json

# 4. Create least-privilege Service Account for GitHub Actions
gcloud iam service-accounts create github-deployer \
  --display-name="GitHub Actions Cloud Run Deployer"

for ROLE in roles/run.admin roles/artifactregistry.writer roles/iam.serviceAccountUser; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${SA_EMAIL}" \
    --role="$ROLE"
done

# 5. Configure Workload Identity Federation (OIDC — zero JSON keys)
gcloud iam workload-identity-pools create "github-pool" \
  --location="global" \
  --display-name="GitHub Actions Pool"

gcloud iam workload-identity-pools providers create-oidc "github-provider" \
  --location="global" \
  --workload-identity-pool="github-pool" \
  --display-name="GitHub Actions Provider" \
  --issuer-uri="[https://token.actions.githubusercontent.com](https://token.actions.githubusercontent.com)" \
  --attribute-mapping="google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository=='${REPO}'"

gcloud iam service-accounts add-iam-policy-binding "$SA_EMAIL" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://[iam.googleapis.com/projects/$](https://iam.googleapis.com/projects/$){PROJECT_NUMBER}/locations/global/workloadIdentityPools/github-pool/attribute.repository/${REPO}"
```

Store the resulting values in **GitHub → Settings → Secrets and variables → Actions**:
- `GCP_PROJECT_ID`
- `GCP_SA_EMAIL`
- `GCP_WIF_PROVIDER` (`projects/<PROJECT_NUMBER>/locations/global/workloadIdentityPools/github-pool/providers/github-provider`)

---

## 3. Databases (Neon Serverless PostgreSQL)

Persistence is hosted on **Neon Free Tier** following the **Database-per-Service** pattern inside a single `quantum-store` project:

| Microservice | Local Port Fallback | Neon Database | GitHub Secrets |
| :--- | :--- | :--- | :--- |
| `catalog-service` | `${PORT:8081}` | `catalog_db` | `CATALOG_DB_URL`, `CATALOG_DB_USER`, `CATALOG_DB_PASSWORD` |
| `inventory-service` | `${PORT:8082}` | `inventory_db` | `INVENTORY_DB_URL`, `INVENTORY_DB_USER`, `INVENTORY_DB_PASSWORD` |

Schema migrations and seed data are executed automatically on startup by **Flyway**.

---

## 4. Automated Deployment Pattern (GitHub Actions → Artifact Registry → Cloud Run)

Each microservice owns a dedicated workflow (`.github/workflows/deploy-<service>.yml`) triggered on pushes to `main` touching its directory:

1. **Build locally** (`docker/build-push-action` with `load: true, push: false`).
2. **Security gate:** Scan the image with **Trivy** (`CRITICAL,HIGH`, `ignore-unfixed: true`, `exit-code: "1"`).
3. **Keyless auth:** Authenticate to GCP via `google-github-actions/auth@v2` using Workload Identity Federation (`permissions: id-token: write`).
4. **Push & Deploy:** Tag with `${{ github.sha }}` and `latest`, push to `us-central1-docker.pkg.dev/<PROJECT_ID>/quantum-store/<service>`, and run `gcloud run deploy` with:
   - `--memory=512Mi --cpu=1 --cpu-boost`
   - `--min-instances=0 --max-instances=2`
   - `--allow-unauthenticated`
   - Custom env-var delimiter (`--set-env-vars="^|^..."`) to safely pass JDBC URLs containing special characters.
5. **Service Discovery:** Dependent services (e.g., `inventory-service`) resolve upstream URLs dynamically via `gcloud run services describe <upstream> --format="value(status.url)"` and inject them as environment variables (`CATALOG_SERVICE_URL`).
6. **Verification:** Smoke-test `https://<service-url>/q/health/ready`.

### Useful Operations Commands (Cloud Shell)
```bash
# List deployed services and live HTTPS URLs
gcloud run services list --region=us-central1 --format="table(SERVICE,URL)"

# Tail live logs for a microservice
gcloud run services logs read catalog-service --region=us-central1 --limit=50
```

---

## 5. Frontend Deployment (Cloudflare Workers Static Assets)

- **Connected Repo:** `gbolanoscruz/quantum-store-poc` (`main` branch).
- **Assets Directory:** `frontend/`.
- **Production URL:** `https://quantum-store-poc.gbolanoscruz.workers.dev`.
- **CORS:** Every Quarkus microservice explicitly whitelists the Cloudflare Workers origin in `application.properties` (`quarkus.http.cors.origins`).

---

## 6. Quick Troubleshooting Reference

| Symptom | Root Cause | Fix |
| :--- | :--- | :--- |
| First request after idle takes ~2–4s | Cloud Run scale-to-zero + Neon wake-up cold start | Expected on $0 tier; `--cpu-boost` is enabled to minimize startup time |
| `gcloud run deploy` fails with container failed to start on port 8080 | Quarkus hardcoded a static port instead of reading `$PORT` \vert{} Ensure `application.properties` uses `quarkus.http.port=${PORT:808x}` |
| GitHub Actions fails at `Authenticate to Google Cloud (OIDC)` | Missing `id-token: write` permission or wrong `GCP_WIF_PROVIDER` / repo condition | Verify `permissions: id-token: write` in workflow and WIF `attribute-condition` matches `gbolanoscruz/quantum-store-poc` |
| Trivy blocks push on Alpine OS CVEs (e.g., `zlib`) | Base image `eclipse-temurin:21-jre-alpine` ships unpatched OS packages | Keep `RUN apk update && apk upgrade --no-cache` in Stage 2 of the `Dockerfile` |
| Frontend shows `NetworkError` on `fetch` | CORS mismatch or outdated service URL | Verify `quarkus.http.cors.origins` includes `https://quantum-store-poc.gbolanoscruz.workers.dev` and `frontend/index.html` points to current `*.run.app` URLs |