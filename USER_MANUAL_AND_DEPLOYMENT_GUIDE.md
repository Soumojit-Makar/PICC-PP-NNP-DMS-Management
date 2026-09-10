# User Manual and Deployment Guide: `PICC-PP-NNP-DMS-Management`

This document provides a comprehensive operational and deployment manual for the **`PICC-PP-NNP-DMS-Management`** microservice within the **Nubo Native Platform (NNP)**. It covers system architecture, configuration, operational workflows, container operations, Kubernetes deployment, and troubleshooting.

---

## Table of Contents

1. [Service Architecture & Role](#1-service-architecture--role)
2. [Prerequisites & System Requirements](#2-prerequisites--system-requirements)
3. [Configuration Reference & Profiles](#3-configuration-reference--profiles)
   - [Application Properties Matrix](#application-properties-matrix)
   - [Configuration Profiles](#configuration-profiles)
   - [Centralized Config Server Integration](#centralized-config-server-integration)
4. [Functional Operations & Lifecycle Workflows](#4-functional-operations--lifecycle-workflows)
   - [Pre-Flight VM Health Verification](#pre-flight-vm-health-verification)
   - [Asynchronous DMS Deployment Execution](#asynchronous-dms-deployment-execution)
   - [In-Memory SSH Key Expiry & Refresh](#in-memory-ssh-key-expiry--refresh)
   - [Remote Docker Container Lifecycle Operations](#remote-docker-container-lifecycle-operations)
   - [Interactive Web Terminal (WebSocket)](#interactive-web-terminal-websocket)
   - [REST API Endpoints Reference](#rest-api-endpoints-reference)
5. [Local Build & Containerization](#5-local-build--containerization)
   - [Local Build with Maven](#local-build-with-maven)
   - [Docker Container Build & Execution](#docker-container-build--execution)
   - [Docker Compose Multi-Container Setup](#docker-compose-multi-container-setup)
6. [Production Deployment on Kubernetes](#6-production-deployment-on-kubernetes)
   - [Kubernetes Deployment & Service Manifest](#kubernetes-deployment--service-manifest)
   - [ConfigMap and Secret Strategy](#configmap-and-secret-strategy)
7. [Troubleshooting & Frequently Asked Questions](#7-troubleshooting--frequently-asked-questions)

---

## 1. Service Architecture & Role

`PICC-PP-NNP-DMS-Management` manages the asynchronous provisioning, execution, monitoring, and interactive maintenance of DMS components on target remote virtual machines via SSH.

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

### Deployment Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    actor User as Client / UI
    participant API as DeployController
    participant Svc as DeploymentService
    participant Cache as SshKeyCacheService
    participant Runner as DeploymentRunner
    participant VM as Target Remote VM

    User->>API: POST /api/dms/create-dms (credentials, envId)
    API->>Svc: submitDeployment(req)
    Svc->>Cache: put(deploymentId, privateKey, passphrase)
    Svc->>Runner: runDeployment(deploymentId, req)
    Svc-->>API: CreateDeploymentResult(id, PENDING)
    API-->>User: 202 Accepted (Location: /deployments/id)
    
    rect rgb(30, 41, 59)
        Note over Runner,VM: Asynchronous Background Deployment
        Runner->>Cache: get(deploymentId)
        Runner->>VM: SSH Connect and verify git
        Runner->>VM: Clone repository and execute deploy-dms.sh
        VM-->>Runner: Return exit code and output
        Runner->>Svc: Update status (DEPLOYED or FAILED)
    end
```

---

## 2. Prerequisites & System Requirements

### Runtime Prerequisites
- **JDK 21 LTS** (Eclipse Temurin or OpenJDK).
- **PostgreSQL 14+** (database `nnp-core-comp`, schema `portal`).
- **Target Remote VM**:
  - Linux OS (Ubuntu, Debian, RHEL, CentOS, Rocky Linux, or Alpine).
  - SSH Server enabled on port 22.
  - Sudo access without password for deployment execution (`sudo -n` or configured wheel/sudoers).
  - Docker Engine installed and running.

---

## 3. Configuration Reference & Profiles

### Application Properties Matrix

| Property Key | Environment Variable | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `server.port` | `SERVER_PORT` | `8080` | HTTP service port |
| `server.servlet.context-path` | `SERVER_SERVLET_CONTEXT_PATH` | `/api/dms` | Application servlet context path |
| `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` | `local` | Active profile (`local`, `dev`, `main`) |
| `git.repository.url` | `GIT_REPOSITORY_URL` | `https://gitlab.example.com/...` | Deployment scripts Git repository |
| `git.repository.branch` | `GIT_REPOSITORY_BRANCH` | `main` | Git branch checked out on remote VM |
| `git.repository.username` | `GIT_REPOSITORY_USERNAME` | `devops` | Git authentication username |
| `git.repository.token` | `GIT_REPOSITORY_TOKEN` | *None* | Git Personal Access Token |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/...` | PostgreSQL JDBC connection URL |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | `postgres` | Database username |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | *None* | Database password |
| `deployment.health.port` | `DEPLOYMENT_HEALTH_PORT` | `9099` | Health port probed on target VM |
| `deployment.disk.threshold` | `DEPLOYMENT_DISK_THRESHOLD` | `80` | Disk usage alert threshold (%) |
| `deployment.scheduler.cron` | `DEPLOYMENT_SCHEDULER_CRON` | `0 */5 * * * *` | Background health check scheduler cron |
| `dms.ssh-key.ttl-minutes` | `DMS_SSH_KEY_TTL_MINUTES` | `60` | In-memory SSH key cache TTL |
| `dms.ssh-key.purge-delay-ms` | `DMS_SSH_KEY_PURGE_DELAY_MS` | `60000` | SSH key cache sweep interval (ms) |
| `dms.ssh.connect-timeout-ms` | `DMS_SSH_CONNECT_TIMEOUT_MS` | `10000` | SSH connection timeout (ms) |
| `logging.level.com.nnp.dms` | `LOG_LEVEL` | `INFO` | Root application log level |

---

## 4. Functional Operations & Lifecycle Workflows

### Pre-Flight VM Health Verification
Verify that the target remote host is online, SSH credentials are valid, and port 9099 responds before triggering full deployment:

```bash
curl -X POST http://localhost:8080/api/dms/check-status \
  -H "Content-Type: application/json" \
  -d '{
    "host": "192.0.2.10",
    "port": 22,
    "username": "admin",
    "privateKey": "-----BEGIN OPENSSH PRIVATE KEY-----\n...\n-----END OPENSSH PRIVATE KEY-----",
    "passphrase": ""
  }'
```

### Asynchronous DMS Deployment Execution
Submit a deployment. The request returns `202 ACCEPTED` with a tracking ID:

```bash
curl -X POST http://localhost:8080/api/dms/create-dms \
  -H "Content-Type: application/json" \
  -d '{
    "envId": "ENV-PROD-01",
    "host": "192.0.2.10",
    "port": 22,
    "username": "admin",
    "password": "master-admin-password",
    "privateKey": "-----BEGIN OPENSSH PRIVATE KEY-----\n...\n-----END OPENSSH PRIVATE KEY-----",
    "passphrase": "",
    "filePath": "/opt/nnp-dms"
  }'
```

### REST API Endpoints Reference

| Method | Path | Summary | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/dms/check-status` | VM Health Check | Tests SSH connectivity and remote component health |
| `POST` | `/api/dms/create-dms` | Create Deployment | Asynchronously triggers remote DMS deployment |
| `GET` | `/api/dms/deployments/{id}` | Get Deployment | Returns current deployment status and component records |
| `GET` | `/api/dms/deployments` | List Deployments | Paginated query filtered by component or status |
| `POST` | `/api/dms/deployments/{id}/ssh-key` | Refresh SSH Key | Re-populates expired in-memory SSH key cache |
| `POST` | `/api/dms/deployments/{id}/refresh-containers` | Refresh Containers | Queries `docker ps` on remote host and updates DB |
| `GET` | `/api/dms/deployments/{id}/containers` | Get Containers | Fetches cached container records |
| `POST` | `/api/dms/deployments/{id}/restart` | Restart Container | Executes `docker restart` on remote container |
| `POST` | `/api/dms/deployments/{id}/remove` | Remove Container | Executes `docker rm -f` on remote container |
| `GET` | `/api/dms/terminal/{id}` | Web Terminal | Serves xterm.js interactive browser console |

---

## 5. Local Build & Containerization

### Local Build with Maven
```bash
./mvnw clean package -DskipTests
```

### Docker Container Build & Execution
```bash
docker build -t picc-pp-nnp-dms-management:latest .
docker run -d -p 8080:8080 --env-file .env picc-pp-nnp-dms-management:latest
```

### Docker Compose Multi-Container Setup
Start the DMS service along with an isolated PostgreSQL database:
```bash
docker compose up -d
docker compose logs -f dms-management
```

---

## 6. Production Deployment on Kubernetes

### Kubernetes Deployment & Service Manifest

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: dms-management-service
  namespace: nnp-core-components
  labels:
    app: dms-management-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: dms-management-service
  template:
    metadata:
      labels:
        app: dms-management-service
    spec:
      containers:
        - name: dms-management
          image: ghcr.io/nubo-native-platform/picc-pp-nnp-dms-management:0.0.1
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8080
              name: http
          envFrom:
            - configMapRef:
                name: dms-config
            - secretRef:
                name: dms-secrets
          resources:
            requests:
              cpu: 100m
              memory: 256Mi
            limits:
              cpu: 500m
              memory: 1024Mi
          readinessProbe:
            httpGet:
              path: /api/dms/actuator/health
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /api/dms/actuator/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 15
---
apiVersion: v1
kind: Service
metadata:
  name: dms-management-service
  namespace: nnp-core-components
spec:
  type: ClusterIP
  selector:
    app: dms-management-service
  ports:
    - port: 8080
      targetPort: 8080
      name: http
```

---

## 7. Troubleshooting & Frequently Asked Questions

### Common Issues

1. **`SSH key expired, please re-enter the key.`**
   - **Root Cause**: The SSH private key cached in volatile memory expired after `dms.ssh-key.ttl-minutes` (default: 60 minutes).
   - **Resolution**: Call `POST /api/dms/deployments/{id}/ssh-key` with the valid private key to re-populate the cache.

2. **`unreachable` or connection timeout during check-status**
   - Verify network security groups and firewall rules between the DMS service and target VM port 22.
   - Verify that the target VM user has permission to connect and sudo privileges.

3. **OpenAPI / Swagger UI Access**
   - Access Swagger UI at `http://<host>:8080/api/dms/swagger-ui.html`.
   - Access OpenAPI 3.0 raw specification at `http://<host>:8080/api/dms/v3/api-docs`.
