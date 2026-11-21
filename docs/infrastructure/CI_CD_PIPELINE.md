# Continuous Integration & Delivery (CI/CD) Pipeline

## Overview
PulseCare 2.0 employs hardened GitHub Actions workflows to enforce automated security, code quality, testing, container validation, and deployment gates.

---

## 1. Workflow Architecture

PulseCare maintains two primary workflows:

### 1.1 Pull Request & Build Validation (`.github/workflows/ci.yml`)
- **Triggers:**
  - Pull requests targeting `main`
  - Direct pushes to `main` and `develop`
- **Security Context:**
  - Top-level least-privilege security permission: `permissions: contents: read`
  - Strict concurrency cancellation: `cancel-in-progress: true`
- **Jobs:**
  1. `backend-build-test`:
     - Sets up Java 17 (Eclipse Temurin).
     - Executes `./mvnw -B test` (Unit tests + Testcontainers integration tests).
     - Compiles production JAR: `./mvnw -B package -DskipTests`.
     - Zero tolerance: `continue-on-error` is strictly disallowed.
  2. `frontend-build-test`:
     - Sets up Node.js 22.
     - Performs clean install: `npm ci`.
     - Executes static analysis: `npm run lint`.
     - Compiles Next.js bundle: `npm run build`.
  3. `container-build-validation`:
     - Depends on `backend-build-test` and `frontend-build-test`.
     - Validates local compose: `docker compose config --quiet`.
     - Verifies fail-closed rejection on missing production compose secrets.
     - Validates production compose schema with synthetic test secrets.
     - Verifies MongoDB host port is unexposed in production compose.
     - Validates backend multi-stage Docker build: `docker build ./backend`.
     - Validates frontend multi-stage Docker build: `docker build ./frontend`.

### 1.2 Production Release & Registry Delivery (`.github/workflows/ci-cd.yml`)
- **Triggers:**
  - Direct pushes to `main` only.
- **Security Context:**
  - Least-privilege top-level permission: `permissions: contents: read`.
  - Step-isolated permission `packages: write` granted strictly to the `publish-images` job.
- **Delivery Strategy:**
  - Authenticates to GitHub Container Registry (`ghcr.io`) using temporary `${{ secrets.GITHUB_TOKEN }}`.
  - Pushes hardened production images:
    - `ghcr.io/<owner>/hospital-management-backend:latest`
    - `ghcr.io/<owner>/hospital-management-frontend:latest`
  - Utilizes GitHub Actions cache (`type=gha`) for optimal build speeds.

---

## 2. Quality Gates & Failure Modes

| Gate | Description | Action on Failure |
| :--- | :--- | :--- |
| **Backend Test Suite** | 47 Unit, Slice & Integration Tests | Blocks merge, halts pipeline immediately |
| **Frontend Lint** | ESLint compliance | Blocks merge, halts pipeline immediately |
| **Frontend Build** | Next.js compilation & TypeScript validation | Blocks merge, halts pipeline immediately |
| **Compose Schema** | Docker compose syntax & fail-closed secret checks | Blocks merge, halts pipeline immediately |
| **Container Build** | Multi-stage Dockerfile build validation | Blocks merge, halts pipeline immediately |
| **Test Isolation** | Dynamic Testcontainers verification | Fails closed if container cannot start |
