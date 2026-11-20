# 🛒 Quantum Store PoC

[![Security & Vulnerability Scan](https://github.com/gbolanoscruz/quantum-store-poc/actions/workflows/security-scan.yml/badge.svg)](https://github.com/gbolanoscruz/quantum-store-poc/actions/workflows/security-scan.yml)
[![License](https://img.shields.io/github/license/gbolanoscruz/quantum-store-poc)](LICENSE)

**Proof of Concept (PoC)** of a modern e-commerce platform built on a cloud-native, event-driven microservices architecture, featuring built-in observability and automated DevSecOps pipelines.

## 🏛️ Architecture & Microservices

The project is structured as a modular **Monorepo** covering the following business and platform domains:

| Microservice | Core Responsibility | Status |
| :--- | :--- | :---: |
| **Orders** | Order lifecycle management and shopping cart | 🚧 Planned |
| **Payments** | Payment processing, gateway integration, and reconciliation | 🚧 Planned |
| **Inventory** | Real-time stock control, reservations, and availability | 🚧 Planned |
| **Shipping** | Shipping rate calculation, logistics, and package tracking | 🚧 Planned |
| **Billing** | Invoicing, tax calculation, and receipts | 🚧 Planned |
| **Sessions / Auth** | User session management, identity, and access control | 🚧 Planned |
| **Languages (i18n)** | Internationalization, translations, and multi-region content | 🚧 Planned |
| **Communications** | Email, SMS, and real-time event notifications | 🚧 Planned |
| **Observability** | Centralized logging, metrics, and distributed tracing | 🚧 Planned |

## 🛠️ Tech Stack *(WIP)*

- **Backend:** Java (Microservices Architecture)
- **Frontend:** Modern Web Framework *(TBD)*
- **Infrastructure:** Docker & Kubernetes (`infra/`)
- **CI/CD & Security:** GitHub Actions, Trivy, CodeQL, Dependabot, and Secret Scanning

## 📂 Monorepo Structure

```text
quantum-store-poc/
├── .github/workflows/   # CI/CD pipelines and automated security scans
├── docs/                # Architecture diagrams, ADRs, and technical docs
├── frontend/            # Modern web storefront client
├── infra/
│   ├── docker/          # Docker Compose and local environment configs
│   └── k8s/             # Kubernetes manifests
└── services/            # Java microservices
```

## 🔒 Security & Workflow

The main branch is protected using GitHub Rulesets:

    All changes must be submitted via Pull Request.

    Trivy automatically scans for CRITICAL and HIGH vulnerabilities on every PR.

    CodeQL, Dependabot, and Secret Scanning run continuously across the repository.

## 🗺️ Roadmap

    [x] Initial Monorepo setup and main branch protection

    [x] Base CI/CD security pipeline with Trivy on GitHub Actions

    [ ] Cutting-edge tech stack definition (Backend & Frontend)

    [ ] System architecture design (API Gateway, sync/async messaging, and databases)

    [ ] First microservice implementation and local Docker setup


---
**Built with 💻 by Gustavo Bolaños**