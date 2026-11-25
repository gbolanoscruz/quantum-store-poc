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

| Microservice | Local Port Fallback | Neon Database | Upstream Env Vars | GitHub Secrets |
| :--- | :--- | :--- | :--- | :--- |
| `catalog-service` | `${PORT:8081}` | `catalog_db` | — | `CATALOG_DB_URL`, `CATALOG_DB_USER`, `CATALOG_DB_PASSWORD` |
| `inventory-service` | `${PORT:8082}` | `inventory_db` | `CATALOG_SERVICE_URL` | `INVENTORY_DB_URL`, `INVENTORY_DB_USER`, `INVENTORY_DB_PASSWORD` |
| `orders-service` | `${PORT:8083}` | `orders_db` | `CATALOG_SERVICE_URL`, `INVENTORY_SERVICE_URL` | `ORDERS_DB_URL`, `ORDERS_DB_USER`, `ORDERS_DB_PASSWORD` |
| `payments-service` | `${PORT:8084}` | `payments_db` | `ORDERS_SERVICE_URL` | `PAYMENTS_DB_URL`, `PAYMENTS_DB_USER`, `PAYMENTS_DB_PASSWORD` |

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

---

## 6. Testing & Observability Verification

### Automated CI & Local Tests
All four microservices are aggregated in the root `pom.xml` and executed in `.github/workflows/ci-tests.yml`:

```powershell
mvn test
```

### Live Health & Prometheus Metrics
Every Cloud Run service exposes `/q/health/ready` and `/q/metrics`:

```powershell
Invoke-RestMethod "[https://payments-service-hlbrzrqapa-uc.a.run.app/q/health/ready](https://payments-service-hlbrzrqapa-uc.a.run.app/q/health/ready)"
Invoke-RestMethod "[https://orders-service-hlbrzrqapa-uc.a.run.app/q/health/ready](https://orders-service-hlbrzrqapa-uc.a.run.app/q/health/ready)"
```

To inspect custom business counters in Prometheus format:

```powershell
(Invoke-RestMethod "[https://orders-service-hlbrzrqapa-uc.a.run.app/q/metrics](https://orders-service-hlbrzrqapa-uc.a.run.app/q/metrics)") -split "`n" | Select-String "quantum_orders_total|http_server_requests"
(Invoke-RestMethod "[https://payments-service-hlbrzrqapa-uc.a.run.app/q/metrics](https://payments-service-hlbrzrqapa-uc.a.run.app/q/metrics)") -split "`n" | Select-String "quantum_payments_total"
```

### Correlated Traces in Google Cloud Logging
1. Run an end-to-end flow from the storefront (**Place Order → Pay → Refund**).
2. Open **Google Cloud Console → Logging → Logs Explorer**.
3. Filter by `resource.type="cloud_run_revision"` and inspect JSON logs from `payments-service` or `orders-service`.
4. Filter or group by the `trace` / `traceId` field to view correlated logs across `payments-service`, `orders-service`, `inventory-service`, and `catalog-service`.

---

## 7. Authentication Service (`auth-service`) & Google OAuth

### Service & Database
- **Cloud Run Service:** `auth-service` (`us-central1`, `512Mi`, `1 CPU`, `--cpu-boost`, `--min-instances=0`, `--max-instances=2`).
- **Endpoint:** `https://auth-service-hlbrzrqapa-uc.a.run.app/api/auth`
- **Database:** Isolated `auth_db` database on Neon PostgreSQL, managed via Flyway (`V1__create_users_table.sql`).

### Required Secrets & Configuration
- **GitHub Actions Secrets:** `AUTH_DB_URL`, `AUTH_DB_USER`, `AUTH_DB_PASSWORD`, `JWT_SIGNING_SECRET`, and `GOOGLE_CLIENT_ID`.
- **Google Cloud Console:** Web OAuth 2.0 Client ID (`*.apps.googleusercontent.com`) with `https://quantum-store-poc.gbolanoscruz.workers.dev` listed under **Authorized JavaScript origins**.
- **Frontend (`frontend/app.js`):** Configured with `AUTH_SERVICE_URL` and the matching `GOOGLE_CLIENT_ID` to render the Google Sign-In button, manage the Bearer JWT in session storage, and display login/sign-up/logout controls.