# PICC-PP-NNP-DMS-Management

[![Java Version](https://img.shields.io/badge/Java-21%20LTS-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.4-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.0.0-6DB33F?logo=spring&logoColor=white)](https://spring.io/projects/spring-cloud)
[![OpenAPI](https://img.shields.io/badge/OpenAPI-3.0%20Swagger-85EA2D?logo=openapiinitiative&logoColor=black)](https://swagger.io/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)]()

Enterprise asynchronous DMS deployment and lifecycle management microservice for the **Nubo Native Platform (NNP)**. Orchestrates remote virtual machine preparation, Git deployment script cloning, automated container operations, interactive WebSocket terminal emulation, and scheduled health reconciliation.

---

## Table of Contents

- [Overview](#overview)
- [Key Architectural Features](#key-architectural-features)
- [Architecture Diagram](#architecture-diagram)
- [Technology Matrix](#technology-matrix)
- [Quick Start](#quick-start)
  - [Prerequisites](#prerequisites)
  - [Configuration](#configuration)
  - [Local Execution](#local-execution)
  - [Docker Execution](#docker-execution)
- [Configuration Reference](#configuration-reference)
- [REST & WebSocket API Reference](#rest--websocket-api-reference)
- [Interactive Web Terminal](#interactive-web-terminal)
- [Project Documentation](#project-documentation)
- [Repository Structure](#repository-structure)
- [Security and Compliance](#security-and-compliance)
- [Contributing](#contributing)
- [License](#license)

---

## Overview

**`PICC-PP-NNP-DMS-Management`** validates customer environment plans, connects to target remote virtual machines over SSH via JSch, clones the DMS deployment scripts from Git, and executes automated deployment pipelines.

The service provides complete operational lifecycle management, including:
- Non-blocking asynchronous deployment dispatch returning immediate `202 Accepted` tracking tokens.
- Zero-persistence in-memory SSH key caching with automated expiry (TTL).
- Remote container state inspection and container management (`ps`, `restart`, `remove`).
- Background cron-scheduled health monitoring of deployed components.
- Interactive VT100 / xterm browser console brokered securely over WebSockets.

---

## Key Architectural Features

- **Asynchronous Execution**: Worker thread pools (`@Async("deploymentExecutor")`) isolate heavy deployment runs from web request threads.
- **Zero-Persistence SSH Security**: Private keys and passphrases are cached solely in volatile memory with configurable TTL (`dms.ssh-key.ttl-minutes`), preventing credential leakage into persistent databases.
- **Strict Shell Sanitization**: User inputs are escaped through single-quoted shell quotation (`shQuote`), neutralizing shell metacharacter injection.
- **Automated Health Reconciliation**: Scheduled background tasks poll remote ports (default `9099`) to transition deployment statuses dynamically (`DEPLOYED`, `DEGRADED`, `FAILED`).
- **Interactive Pseudo-Terminal**: Full-duplex WebSocket session bridging xterm.js frontend with remote JSch SSH shell sessions.
- **OpenAPI 3 / Swagger Documentation**: Embedded SpringDoc UI for interactive endpoint exploration and testing.

---

## Architecture Diagram

```mermaid
graph TD
    classDef client fill:#0284C7,stroke:#38BDF8,stroke-width:2px,color:#FFFFFF;
    classDef comp fill:#0F172A,stroke:#38BDF8,stroke-width:1px,color:#E2E8F0;
    classDef ext fill:#D97706,stroke:#F59E0B,stroke-width:2px,color:#FFFFFF;
    classDef db fill:#047857,stroke:#10B981,stroke-width:2px,color:#FFFFFF;

    Clients["<b>Client / NNP Web Portal</b><br/>REST API (Port 8080) & WebSocket"]:::client

    subgraph ServiceLayer ["<b>PICC-PP-NNP-DMS-Management</b>"]
        Controller["<b>REST Controller</b><br/>DeployController"]:::comp
        Svc["<b>Deployment Service</b><br/>DeploymentService"]:::comp
        Runner["<b>Async Deployment Engine</b><br/>DeploymentRunner (@Async)"]:::comp
        Scheduler["<b>Health Check Scheduler</b><br/>DeploymentScheduler (Cron)"]:::comp
        KeyCache["<b>Volatile Key Cache</b><br/>SshKeyCacheService (TTL 60m)"]:::comp
        Terminal["<b>Interactive Terminal</b><br/>TerminalHandler (WebSocket)"]:::comp
        Ssh["<b>SSH & Remote Execution</b><br/>SshService (JSch Engine)"]:::comp
    end

    Database[("<b>PostgreSQL 16 Database</b><br/>Schema: portal")]:::db
    GitRepo["<b>Git Script Repository</b><br/>Deployment Bash Scripts"]:::ext
    RemoteVM["<b>Target Remote VM / Node</b><br/>SSH Port 22 | Docker Engine"]:::ext

    Clients -->|REST /api/dms/*| Controller
    Clients -.->|WebSocket /terminal| Terminal
    Controller --> Svc
    Svc --> Runner
    Svc --> KeyCache
    Svc --> Database
    Scheduler --> Ssh
    Runner --> Ssh
    Terminal --> Ssh
    Ssh -->|Execute via SSH| RemoteVM
    RemoteVM -.->|Clone deploy-dms.sh| GitRepo
```

---

## Technology Matrix

| Component | Technology / Library | Version |
| :--- | :--- | :--- |
| Runtime | Eclipse Temurin JDK | 21 LTS |
| Core Framework | Spring Boot | 3.5.4 |
| Cloud Config | Spring Cloud Starter Config | 2025.0.0 |
| Persistence | Spring Data JPA / Hibernate | 3.5.4 |
| Database Driver | PostgreSQL JDBC Driver | 42.7.x |
| SSH Client | JSch (mwiede fork) | 0.2.17 |
| API Docs | SpringDoc OpenAPI Starter | 2.8.5 |
| Terminal UI | xterm.js + fit addon | 5.3.0 |
| SAST Scanner | SpotBugs + FindSecBugs | 4.8.6.0 / 1.13.0 |
| SCA Scanner | OWASP Dependency-Check | 10.0.4 |
| SBOM Generator | CycloneDX Maven Plugin | 2.9.1 |

---

## Quick Start

### Prerequisites
- JDK 21 installed (`java -version`).
- PostgreSQL 14+ running locally (or via Docker).
- Maven 3.9+ (or use `./mvnw.cmd` / `./mvnw`).

### Configuration
Copy the template and configure local environment variables:
```bash
cp .env.example .env
```

### Local Execution
```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

The service starts on port **8080** with context path **`/api/dms`**. Access Swagger UI at:
```
http://localhost:8080/api/dms/swagger-ui.html
```

### Docker Execution
```bash
# Build container image
docker build -t picc-pp-nnp-dms-management:latest .

# Run with Docker Compose
docker compose up -d
```

---

## Configuration Reference

| Property Key | Environment Variable | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `server.port` | `SERVER_PORT` | `8080` | Service HTTP port |
| `server.servlet.context-path` | `SERVER_SERVLET_CONTEXT_PATH` | `/api/dms` | Servlet context path |
| `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` | `local` | Active configuration profile |
| `git.repository.url` | `GIT_REPOSITORY_URL` | `https://gitlab.example.com/...` | Deployment scripts Git repository |
| `git.repository.branch` | `GIT_REPOSITORY_BRANCH` | `main` | Target Git branch |
| `git.repository.username` | `GIT_REPOSITORY_USERNAME` | `devops` | Git authentication username |
| `git.repository.token` | `GIT_REPOSITORY_TOKEN` | *None* | Git Personal Access Token |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/nnp-core-comp` | Database JDBC URL |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | `postgres` | Database username |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | *None* | Database password |
| `deployment.health.port` | `DEPLOYMENT_HEALTH_PORT` | `9099` | Remote host health check port |
| `deployment.disk.threshold` | `DEPLOYMENT_DISK_THRESHOLD` | `80` | Disk usage alert threshold percentage |
| `deployment.scheduler.cron` | `DEPLOYMENT_SCHEDULER_CRON` | `0 */5 * * * *` | Background health check scheduler cron |
| `dms.ssh-key.ttl-minutes` | `DMS_SSH_KEY_TTL_MINUTES` | `60` | In-memory SSH key cache TTL in minutes |
| `dms.ssh-key.purge-delay-ms` | `DMS_SSH_KEY_PURGE_DELAY_MS` | `60000` | SSH key cache sweep interval in milliseconds |
| `dms.ssh.connect-timeout-ms` | `DMS_SSH_CONNECT_TIMEOUT_MS` | `10000` | SSH connection timeout in milliseconds |

---

## REST & WebSocket API Reference

All REST endpoints reside under the base context path `/api/dms`:

### 1. Pre-Flight VM Health Check
- **`POST /api/dms/check-status`**
- Verifies remote VM reachability, SSH credentials, and health endpoints.
```json
{
  "host": "192.0.2.10",
  "port": 22,
  "username": "admin",
  "privateKey": "-----BEGIN OPENSSH PRIVATE KEY-----\n...\n-----END OPENSSH PRIVATE KEY-----",
  "passphrase": ""
}
```

### 2. Create DMS Deployment
- **`POST /api/dms/create-dms`**
- Returns `202 Accepted` with `Location: /api/dms/deployments/{id}` header.
```json
{
  "envId": "ENV-001",
  "host": "192.0.2.10",
  "port": 22,
  "username": "admin",
  "password": "master-password",
  "privateKey": "-----BEGIN OPENSSH PRIVATE KEY-----\n...\n-----END OPENSSH PRIVATE KEY-----",
  "passphrase": "",
  "filePath": "/opt/nnp-dms"
}
```

### 3. Track Deployment Status
- **`GET /api/dms/deployments/{id}`**
- Returns deployment state (`PENDING`, `RUNNING`, `DEPLOYED`, `FAILED`), execution timestamps, and component statuses.

### 4. Refresh Expired SSH Key
- **`POST /api/dms/deployments/{id}/ssh-key`**
- Refreshes the in-memory SSH credential cache when the TTL has expired.

### 5. Docker Container Operations
- **`POST /api/dms/deployments/{id}/refresh-containers`**: Re-queries `docker ps` on remote host.
- **`GET /api/dms/deployments/{id}/containers`**: Returns cached container inventory.
- **`POST /api/dms/deployments/{id}/restart`**: Issues `docker restart` command on container.
- **`POST /api/dms/deployments/{id}/remove`**: Issues `docker rm -f` command on container.

---

## Interactive Web Terminal

The service includes an embedded browser terminal powered by **xterm.js**:
- Web UI: `http://localhost:8080/api/dms/terminal.html?deploymentId={id}`
- WebSocket Protocol: `ws://localhost:8080/api/dms/terminal/{deploymentId}`
- Supports VT100 control sequences, interactive keystroke streaming, and dynamic terminal window resizing (`resize` frames).

---

## Project Documentation

- [User Manual & Deployment Guide](USER_MANUAL_AND_DEPLOYMENT_GUIDE.md): Operational architecture, sequence flows, Kubernetes manifests, and troubleshooting.
- [Development Guidelines](DEVELOPMENT_GUIDELINES.md): Architectural principles, coding conventions, DevSecOps scanning, and PR checklists.
- [Contributing Guidelines](CONTRIBUTING.md): Workflow standards, Apache 2.0 licensing, and submission process.
- [Security Policy](SECURITY.md): Vulnerability disclosure and zero-secrets mandate.
- [Code of Conduct](CODE_OF_CONDUCT.md): CNCF community standards.
- [Maintainers](MAINTAINERS.md): Project maintainer contacts.

---

## Repository Structure

```
.
├── .env.example                                # Configuration template
├── .gitattributes                              # Text and LF normalization
├── .gitignore                                  # Secret, artifact, and IDE exclusions
├── .github/workflows/ci-cd.yml                 # GitHub Actions build, scan & publish
├── CODE_OF_CONDUCT.md                          # CNCF Code of Conduct
├── CONTRIBUTING.md                             # Contribution guidelines
├── DEVELOPMENT_GUIDELINES.md                   # Developer handbook
├── docker-compose.yml                          # Multi-container orchestration
├── Dockerfile                                  # Hardened non-root runtime image
├── LICENSE                                     # Apache License 2.0
├── MAINTAINERS.md                              # Maintainer roster
├── pom.xml                                     # Maven build definition & profiles
├── README.md                                   # Project documentation
├── SECURITY.md                                 # Security and reporting policy
├── spotbugs-exclude.xml                        # SAST exclusion filter
├── USER_MANUAL_AND_DEPLOYMENT_GUIDE.md         # Operational & deployment manual
└── src/
    ├── main/java/com/nnp/dms/                  # Microservice source code
    └── test/java/com/nnp/dms/                  # Unit and mock controller tests
```

---

## Security and Compliance

Every build automatically executes DevSecOps validation:
- **SAST (SpotBugs + FindSecBugs)**: Zero security warnings policy (`./mvnw spotbugs:check`).
- **SCA (OWASP Dependency-Check)**: Dependency vulnerability scanner (`./mvnw dependency-check:check`).
- **SBOM (CycloneDX)**: Standardized Software Bill of Materials generation (`./mvnw cyclonedx:makeAggregateBom`).

---

## Contributing

Contributions are welcome under the **Apache License 2.0**. Please read [CONTRIBUTING.md](CONTRIBUTING.md) and [DEVELOPMENT_GUIDELINES.md](DEVELOPMENT_GUIDELINES.md) before submitting pull requests.

---

## License

This project is licensed under the **Apache License 2.0**. See the [LICENSE](LICENSE) file for details.
