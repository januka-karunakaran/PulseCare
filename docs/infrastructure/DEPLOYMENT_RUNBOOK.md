# Production Deployment Runbook

## 1. Pre-Deployment Verification

Before executing any deployment to staging or production:
1. Ensure all CI quality gates passed on the target commit in `main`.
2. Verify that production secrets are prepared in the target environment:
   - `MONGODB_URI` (Internal cluster URI with authentication)
   - `JWT_SECRET` (Unique, >= 256-bit cryptographically secure key)
   - `CORS_ALLOWED_ORIGINS` (Exact domain names, no wildcards)
3. Ensure current database backup has completed and is verified according to `docs/infrastructure/BACKUP_RECOVERY.md`.

---

## 2. Step-by-Step Deployment Procedure

### Step 1: Clone or Fetch Release Artifacts
```bash
git fetch origin main
git checkout main
```

### Step 2: Configure Production Environment Variables
Ensure `.env` in the deployment directory contains all non-default production values:
```bash
cp .env.example .env
# Edit .env with production secrets (never commit this file)
chmod 600 .env
```

### Step 3: Validate Compose Configuration
```bash
docker compose -f docker-compose.prod.yml config --quiet
```

### Step 4: Pull or Build Production Images
```bash
docker compose -f docker-compose.prod.yml build
```

### Step 5: Start Persistence & Dependencies
```bash
docker compose -f docker-compose.prod.yml up -d mongodb
```
Verify MongoDB is healthy:
```bash
docker compose -f docker-compose.prod.yml ps mongodb
```

### Step 6: Start Application Services
```bash
docker compose -f docker-compose.prod.yml up -d backend frontend
```

### Step 7: Verify Service Health & Readiness
Probe the backend readiness endpoint:
```bash
curl -f http://127.0.0.1:8080/api/health/readiness
```
Expected response:
```json
{
  "success": true,
  "message": "Service and dependencies are ready",
  "data": {
    "status": "UP",
    "database": "UP",
    "service": "Hospital Management System Backend"
  }
}
```

Probe the frontend service:
```bash
curl -I http://127.0.0.1:3000
```
Expected response: HTTP 200 / 307.

---

## 3. Rollback Procedure

If readiness checks fail, high error rates occur, or `ProductionEnvironmentValidator` halts startup:

1. **Check Logs:**
   ```bash
   docker compose -f docker-compose.prod.yml logs backend
   ```
2. **Revert to Previous Known-Good Release:**
   ```bash
   git checkout <previous-stable-tag-or-commit>
   docker compose -f docker-compose.prod.yml up -d --build
   ```
3. **Database Restore (If Schema Corruption Occurred):**
   Follow the emergency recovery steps in `docs/infrastructure/BACKUP_RECOVERY.md`.
