# CI/CD Pipeline Architecture & Operations

## Overview
PulseCare 2.0 utilizes a unified GitHub Actions pipeline (`.github/workflows/ci.yml`) to enforce code quality, security controls, and deployment integrity across both the Spring Boot backend and Next.js frontend.

## Pipeline Trigger Matrix

| Event | Target Branches | Executed Jobs |
| :--- | :--- | :--- |
| `pull_request` | `main` | `backend-tests`, `frontend-checks`, `container-validation` |
| `push` | `develop` | `backend-tests`, `frontend-checks`, `container-validation` |
| `push` | `main` | `backend-tests`, `frontend-checks`, `container-validation`, `publish-images` |

## Detailed Job Execution

### 1. `backend-tests`
- **Environment:** Ubuntu Latest, Java 17 (Eclipse Temurin)
- **Services:** MongoDB 7.0 container on port 27017
- **Execution:**
  - `chmod +x backend/mvnw`
  - `./mvnw -B test`
  - Surefire report artifact uploaded on failure via `actions/upload-artifact@v4`
  - `./mvnw -B package -DskipTests` to verify artifact packaging.

### 2. `frontend-checks`
- **Environment:** Ubuntu Latest, Node.js 22
- **Execution:**
  - `npm ci` with cache dependency path tracking `package-lock.json`
  - `npm run lint` (ESLint)
  - `npm run build` (Next.js 16 production build)

### 3. `container-validation`
- **Dependencies:** Requires both `backend-tests` and `frontend-checks` to succeed.
- **Execution:**
  - Validates `docker-compose.yml` schema (`docker compose config --quiet`).
  - Verifies fail-closed enforcement on `docker-compose.prod.yml` (ensures failure if production secrets are missing).
  - Validates `docker-compose.prod.yml` with synthetic secret inputs.
  - Verifies MongoDB host port 27017 is not exposed in production compose.
  - Builds Backend Dockerfile with Buildx (dry-run).
  - Builds Frontend Dockerfile with Buildx (dry-run).

### 4. `publish-images`
- **Condition:** Runs exclusively on direct `push` to `refs/heads/main`.
- **Target Registry:** GitHub Container Registry (`ghcr.io/januka-karunakaran/hospital-management-*`).
- **Permissions:** `packages: write`, `contents: read`.

## Local Developer Pre-Push Quality Gate

Developers must run the pre-push quality gate script before opening or updating pull requests:
- **Bash / Linux / macOS:** `./scripts/verify-quality.sh`
- **PowerShell / Windows:** `.\scripts\verify-quality.ps1`
