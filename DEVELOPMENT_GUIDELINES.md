# Development Guidelines and Contribution Standards: `PICC-PP-NNP-DMS-Management`

This document defines the architectural standards, development workflows, coding conventions, and security requirements for contributors to **`PICC-PP-NNP-DMS-Management`**.

---

## Table of Contents

1. [Architecture & Design Principles](#1-architecture--design-principles)
2. [Development Environment Setup](#2-development-environment-setup)
3. [Package Structure & Code Navigation](#3-package-structure--code-navigation)
4. [Coding Standards & Best Practices](#4-coding-standards--best-practices)
   - [Asynchronous Deployment Execution](#asynchronous-deployment-execution)
   - [Zero-Persistence SSH Key Security](#zero-persistence-ssh-key-security)
   - [Remote VM Shell Execution & Quoting](#remote-vm-shell-execution--quoting)
   - [Interactive WebSocket Terminal](#interactive-websocket-terminal)
   - [Exception Handling & Status Codes](#exception-handling--status-codes)
   - [Sensitive Data Masking](#sensitive-data-masking)
5. [Security, Code Quality & Compliance Tooling](#5-security-code-quality--compliance-tooling)
   - [SAST: SpotBugs & FindSecBugs](#sast-spotbugs--findsecbugs)
   - [SCA: OWASP Dependency-Check](#sca-owasp-dependency-check)
   - [SBOM: CycloneDX Aggregate Generation](#sbom-cyclonedx-aggregate-generation)
   - [Checkstyle: Google Java Style](#checkstyle-google-java-style)
6. [Git Workflow & Branching Strategy](#6-git-workflow--branching-strategy)
   - [Branch Naming Conventions](#branch-naming-conventions)
   - [Conventional Commits](#conventional-commits)
7. [Pull Request (PR) Checklist](#7-pull-request-pr-checklist)
8. [Release Lifecycle & Versioning](#8-release-lifecycle--versioning)

---

## 1. Architecture & Design Principles

`PICC-PP-NNP-DMS-Management` serves as the asynchronous orchestration and lifecycle engine for deploying and managing DMS instances on target remote VMs. It operates under core architectural principles:

1. **Non-Blocking Asynchronous Operations**: Heavy operations such as repository cloning, system dependency installation, and script execution execute asynchronously inside managed thread pools (`@Async("deploymentExecutor")`), immediately returning `202 ACCEPTED` with a trackable deployment location header.
2. **Zero-Persistence SSH Key Security**: Private SSH keys and passphrases supplied in API requests are strictly cached in volatile memory with configurable TTL (`dms.ssh-key.ttl-minutes`). They are **never stored** in the database or written to disk.
3. **Safe Shell Execution & Command Sanitization**: All user-provided strings (paths, identifiers, credentials) interpolated into remote bash execution channels must pass strict shell quoting (`shQuote`) to eliminate command injection vulnerabilities.
4. **Resilient Automated Reconciliation**: A scheduled background worker checks health endpoints on target VMs at regular intervals, updating lifecycle states (`DEPLOYED`, `DEGRADED`, `FAILED`) without blocking application request threads.
5. **Interactive Diagnostics via WebSocket**: Real-time SSH interactive terminals are brokered through WebSockets using JSch pseudo-terminal emulation, allowing administrators to debug VMs directly without exposing direct SSH ports publicly.

---

## 2. Development Environment Setup

### Required Tools
- **JDK 21** (Eclipse Temurin 21 or OpenJDK 21 LTS).
- **Maven 3.9+** (or use the included `./mvnw.cmd` / `./mvnw`).
- **PostgreSQL 14+** (local instance or via `docker compose up -d postgres`).
- **IDE**: IntelliJ IDEA, Eclipse, or VS Code with Java & Spring extensions.

### Initial Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/Nubo-Native-Platform/PICC-PP-NNP-DMS-Management.git
   cd PICC-PP-NNP-DMS-Management
   ```
2. Copy environment template:
   ```bash
   cp .env.example .env
   ```
3. Compile and run test suite:
   ```bash
   ./mvnw clean test
   ```

---

## 3. Package Structure & Code Navigation

```
com.nnp.dms
├── VmDeployApplication.java    # Spring Boot application bootstrap
├── config/                     # Configuration beans (Async, Git, WebSocket, OpenAPI)
│   ├── AsyncConfig.java
│   ├── GitProperties.java
│   ├── OpenApiConfig.java
│   └── WebSocketConfig.java
├── controller/                 # REST controllers
│   └── DeployController.java
├── dto/                        # Request and response data transfer objects
├── entity/                     # JPA entities (Deployments, Containers, Actions, Logs)
├── exception/                  # Custom exceptions and exception handlers
├── repository/                 # Spring Data JPA repositories
└── service/                    # Core business logic & SSH engine
    ├── ContainerService.java       # Docker container inspection & restart
    ├── DeploymentRunner.java       # Async deployment execution worker
    ├── DeploymentScheduler.java    # Scheduled background health poller
    ├── DeploymentService.java      # Deployment submission & retrieval
    ├── EnvActivityLogService.java  # Environment audit trail
    ├── EnvironmentPlanService.java # Customer environment plan validation
    ├── SshKeyCacheService.java     # In-memory SSH credential cache with TTL
    ├── SshService.java             # Low-level JSch SSH execution engine
    └── TerminalHandler.java        # WebSocket pseudo-terminal session broker
```

---

## 4. Coding Standards & Best Practices

### Asynchronous Deployment Execution
- All mutative, long-running operations must execute inside `DeploymentRunner` using `@Async("deploymentExecutor")`.
- The database record must transition to `RUNNING` before executing remote commands and record periodic heartbeat timestamps.

### Zero-Persistence SSH Key Security
- Private keys must never be mapped to JPA entities.
- When cached keys expire, operations must return a clear `401`/`502` with message `SSH key expired, please re-enter the key.`

### Remote VM Shell Execution & Quoting
- Always wrap variable arguments in `shQuote(value)`.
- Use heredoc syntax with single-quoted delimiters (`<<'DMS_SCRIPT'`) to prevent variable expansion on the local side.

### Interactive WebSocket Terminal
- Terminal sessions run over WebSockets with standard terminal resize (`cols`, `rows`) event handling.
- Reader threads must be daemon threads and properly terminated upon session close.

---

## 5. Security, Code Quality & Compliance Tooling

This project integrates automated DevSecOps scanners in Maven:

### SAST: SpotBugs & FindSecBugs
Executes static analysis for security bugs, null pointer dereferences, and bad practices:
```bash
./mvnw spotbugs:check
```

### SCA: OWASP Dependency-Check
Scans dependencies for known CVEs:
```bash
./mvnw dependency-check:check
```

### SBOM: CycloneDX Aggregate Generation
Produces a comprehensive software bill of materials in JSON format (`target/bom.json`):
```bash
./mvnw cyclonedx:makeAggregateBom
```

### Checkstyle: Google Java Style
Enforces uniform formatting:
```bash
./mvnw checkstyle:check
```

---

## 6. Git Workflow & Branching Strategy

- **`main`**: Production-ready branch. Direct pushes are protected.
- **`develop`**: Integration branch for new features.
- **Feature Branches**: `feature/<feature-name>` branched from `develop`.
- **Bugfix Branches**: `bugfix/<issue-name>` branched from `develop`.

### Conventional Commits
All commits must follow the conventional commit format:
```
<type>(<scope>): <subject>

<body>
```
Allowed types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `ci`.

---

## 7. Pull Request (PR) Checklist

Before submitting a PR, verify:
- [ ] `./mvnw clean test` passes with 0 failures.
- [ ] `./mvnw spotbugs:check` reports 0 security findings.
- [ ] No secrets, tokens, internal IP addresses, or `.env` files are included.
- [ ] All new public APIs have OpenAPI descriptions.

---

## 8. Release Lifecycle & Versioning

Releases follow Semantic Versioning (`vMAJOR.MINOR.PATCH`). Publishing to GitHub Packages is triggered automatically upon tagging a commit `v*.*.*` or merging into `main`.
