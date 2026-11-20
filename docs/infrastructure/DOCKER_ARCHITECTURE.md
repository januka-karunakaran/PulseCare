# Docker Container Architecture & Hardening Guide

## Overview
PulseCare 2.0 container architecture provides reproducible, secure, and production-ready packaging for the Spring Boot backend, Next.js frontend, and MongoDB persistence layer.

---

## 1. Multi-Stage Dockerfile Designs

### 1.1 Backend (`backend/Dockerfile`)
- **Stage 1: Builder (`eclipse-temurin:17-jdk-jammy`)**
  - Dependency layer caching: Copies `mvnw`, `.mvn`, and `pom.xml` before source code.
  - Compiles and packages executable JAR using `./mvnw -B package -DskipTests`.
- **Stage 2: Runtime (`eclipse-temurin:17-jre-jammy`)**
  - Minimal JRE footprint (Ubuntu Jammy minimal base).
  - Unprivileged user creation:
    ```dockerfile
    RUN groupadd -g 10001 hms && useradd -u 10001 -g hms -s /bin/bash -m hms
    USER hms:hms
    ```
  - JVM Container-Aware memory flags:
    `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`
  - Built-in Healthcheck:
    Probes `http://127.0.0.1:8080/api/health/readiness` using `curl` every 30s.

### 1.2 Frontend (`frontend/Dockerfile`)
- **Stage 1: Dependencies (`node:22-alpine`)**
  - Installs production dependencies via `npm ci --omit=dev`.
- **Stage 2: Builder (`node:22-alpine`)**
  - Generates optimized Next.js standalone build via `npm run build`.
- **Stage 3: Runner (`node:22-alpine`)**
  - Unprivileged user execution:
    ```dockerfile
    USER node
    ```
  - Copies only standalone server assets (`.next/standalone`, `.next/static`, `public`) with `--chown=node:node`.
  - Built-in Healthcheck:
    Probes `http://localhost:3000/` using `wget -qO-` every 30s.

---

## 2. `.dockerignore` Coverage

Separate `.dockerignore` files exist at repository root, `backend/`, and `frontend/` to strictly prevent build-time secret or credential leakage:
- `.env*`, `.env`, `application-secret.properties`
- `.git`, `.github`
- `node_modules`, `.next`
- `target`, `*.class`
- `*.pem`, `*.key`, `*.dump`

---

## 3. Docker Compose Orchestration

### 3.1 Local Development (`docker-compose.yml`)
- Backend, Frontend, and MongoDB wired on private bridge network `hms-net`.
- Host port binding:
  - Backend: `8080:8080`
  - Frontend: `3000:3000`
  - MongoDB: `127.0.0.1:27017:27017` (strictly bound to localhost loopback, not `0.0.0.0`).
- Resource limits applied:
  - Backend: 1.5 CPUs, 1.5 GB memory limit.
  - Frontend: 1.0 CPU, 1.0 GB memory limit.
  - MongoDB: 1.0 CPU, 1.0 GB memory limit.
- JSON File log rotation: `max-size: 10m`, `max-file: 3`.

### 3.2 Production Deployment (`docker-compose.prod.yml`)
- **MongoDB Network Isolation:**
  - MongoDB has **NO host port published** (`ports:` block removed).
  - Accessible strictly internally by the backend container over `hms-prod-net`.
- **Mandatory Environment Injection:**
  - Passwords and secrets (`${MONGO_ROOT_PASSWORD}`, `${JWT_SECRET}`) are strictly required without insecure default fallbacks.
- **Restart Policies:**
  - `restart: unless-stopped` configured across all services.
- **Resource Constraints & Log Rotation:**
  - Standardized memory limits, CPU reservations, and 10MB/3-file log limits.
