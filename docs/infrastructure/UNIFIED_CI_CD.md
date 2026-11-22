# Unified CI/CD Architecture & Pipeline Specification

## Executive Overview
PulseCare 2.0 previously suffered from duplicate workflow execution on pull requests targeting `main`. Two distinct workflows (`.github/workflows/ci.yml` and `.github/workflows/ci-cd.yml`) both triggered simultaneously, executing duplicate Maven test runs and Next.js builds. Furthermore, an unsafe fallback command (`./mvnw -B test || mvn -B test`) hid initial build failures and executed tests twice upon failure.

This document describes the unified, consolidated CI/CD architecture established in `.github/workflows/ci.yml`.

---

## 1. Consolidated Pipeline Architecture

A single authoritative workflow file (`.github/workflows/ci.yml`) now governs all quality checks and deployments:

```mermaid
graph TD
    PR[Pull Request to main] --> BT[backend-tests]
    PR --> FC[frontend-checks]
    BT --> CV[container-validation]
    FC --> CV
    PUSH[Push to main] --> BT2[backend-tests]
    PUSH --> FC2[frontend-checks]
    BT2 --> CV2[container-validation]
    FC2 --> CV2
    CV2 --> PI[publish-images (GHCR)]
```

### Job Specifications

| Job Key | Name | Trigger | Dependencies | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| `backend-tests` | `backend-tests` | PR, Push (main, develop) | None | Java 17 compile, Spring Boot tests, MongoDB service, clinical security checks, Surefire report artifact |
| `frontend-checks` | `frontend-checks` | PR, Push (main, develop) | None | Node 22, npm ci, ESLint, Next.js 16 production build |
| `container-validation` | `container-validation` | PR, Push (main, develop) | `[backend-tests, frontend-checks]` | Compose schema check, fail-closed production config validation, Dockerfile builds |
| `publish-images` | `publish-images` | Push to `main` ONLY | `[backend-tests, frontend-checks, container-validation]` | Publishes production container images to GitHub Container Registry (ghcr.io) |

---

## 2. Unsafe Fallback Removal & Maven Hardening

### The Problem
Previously, both workflows contained:
```bash
./mvnw -B test || mvn -B test
```
When a test legitimately failed:
1. `./mvnw -B test` exited with code 1.
2. The shell `||` caught code 1 and invoked `mvn -B test`.
3. If `mvn` was absent or failed again, the failure output was obscured and duplicate logs filled the CI runner.

### The Solution
Replaced with the deterministic, cached Maven wrapper:
```yaml
- name: Make Maven Wrapper Executable
  run: chmod +x backend/mvnw

- name: Run Backend Unit & Integration Tests
  working-directory: ./backend
  run: ./mvnw -B test
  env:
    MAIL_ENABLED: 'false'
    JWT_SECRET: ci-only-jwt-secret-with-at-least-256-bits-entropy-guaranteed
    DOCKER_API_VERSION: '1.44'

- name: Upload Surefire Test Reports
  if: failure()
  uses: actions/upload-artifact@v4
  with:
    name: surefire-reports
    path: backend/target/surefire-reports
```

---

## 3. GitHub Branch Protection Migration

Repositories with branch protection rules targeting `main` must update their required status checks.

### Obsolete Check Names (To Be Removed)
- `Backend Build & Test (Java 17 / Spring Boot)`
- `Hospital Management System CI/CD / Backend tests`
- `Hospital Management System CI/CD / Frontend checks`
- `Frontend Build & Test (Next.js 16 / React 19)`
- `Container & Compose Architecture Validation`

### Authoritative Check Names (To Be Required)
1. `backend-tests`
2. `frontend-checks`
3. `container-validation`

### Branch Protection Settings
In GitHub: **Settings > Branches > Branch protection rules > main**:
- Require status checks to pass before merging: **Enabled**
- Require branches to be up to date before merging: **Enabled**
- Select: `backend-tests`, `frontend-checks`, `container-validation`
- Deselect obsolete legacy checks.
