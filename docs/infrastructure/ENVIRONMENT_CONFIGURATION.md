# Environment & Secrets Configuration Guide

## 1. Environment Segmentation

PulseCare 2.0 strictly separates configurations across three environments:

| Environment | Purpose | Database Strategy | Secret Requirements | Index Creation |
| :--- | :--- | :--- | :--- | :--- |
| **Development** (`dev`) | Local coding & debugging | Local MongoDB (`127.0.0.1:27017`) | Development defaults allowed | Auto-creation enabled |
| **Test** (`test`) | CI and automated regression | Disposable Testcontainers container | Test secrets, isolated test db | Auto-creation enabled |
| **Production** (`prod`) | Live patient hospital operations | Production cluster / internal network | Strict fail-closed validation | Auto-creation disabled |

---

## 2. Required Configuration Inventory

| Variable | Description | Allowed in Prod | Example Placeholder |
| :--- | :--- | :--- | :--- |
| `MONGODB_URI` | Full connection URI with credentials | Internal cluster URI only (No localhost) | `mongodb://user:pass@mongo-prod:27017/hms?authSource=admin` |
| `MONGODB_DATABASE` | Target database name | Production DB name | `hospital_management_db` |
| `SERVER_PORT` | HTTP port for backend | Number (typically 8080) | `8080` |
| `JWT_SECRET` | Cryptographic secret for signing tokens | Minimum 256 bits (32 bytes), unique | 64-character hex string |
| `JWT_EXPIRATION` | Token TTL in milliseconds | Positive integer | `86400000` (24h) |
| `CORS_ALLOWED_ORIGINS`| Allowed browser origins | Specific domains only (No `*`) | `https://hospital.example.com` |
| `WS_ALLOWED_ORIGINS`  | Allowed WebSocket origins | Specific domains only (No `*`) | `https://hospital.example.com` |
| `NEXT_PUBLIC_API_BASE`| Next.js API client endpoint | Gateway / Backend API URL | `https://api.hospital.example.com/api` |
| `NEXT_PUBLIC_WS_URL`  | Next.js WebSocket endpoint | Gateway / Backend WS URL | `wss://api.hospital.example.com/ws` |
| `MAIL_ENABLED`        | Password reset email notifications | `true` or `false` | `true` |
| `MAIL_HOST`           | SMTP server hostname | Valid SMTP server | `smtp.mailgun.org` |
| `MAIL_PORT`           | SMTP server port | Integer (587, 465) | `587` |
| `MAIL_USERNAME`       | SMTP authenticated username | Valid username | `notifications@hospital.example.com` |
| `MAIL_PASSWORD`       | SMTP authenticated password | Secure secret | App-specific credential |

---

## 3. Fail-Closed Startup Validation (`ProductionEnvironmentValidator`)

When running with `SPRING_PROFILES_ACTIVE=prod`:
1. `MONGODB_URI`:
   - Must be present and non-empty.
   - Refuses startup if pointing to `localhost` or `127.0.0.1`.
2. `JWT_SECRET`:
   - Must be present and non-empty.
   - Refuses startup if equal to default dev secret or contains placeholder substrings.
   - Enforces a minimum length of 32 bytes (256 bits) of entropy.
3. `CORS_ALLOWED_ORIGINS`:
   - Must be present and non-empty.
   - Refuses startup if containing wildcard `*`.

Any violation results in an immediate `IllegalStateException` halting application startup before opening network listeners.

---

## 4. Secret Rotation Protocol

1. **JWT Secret Rotation:**
   - Generate a new 256-bit cryptographically secure key:
     `openssl rand -hex 32`
   - Schedule key rotation during maintenance window or utilize dual-key verification in future enhancements.
   - Updating `JWT_SECRET` invalidates existing access tokens, requiring users to refresh or re-authenticate.
2. **Database Credential Rotation:**
   - Create new MongoDB user credentials in MongoDB replica set.
   - Update `MONGODB_URI` in production secret manager / environment file.
   - Perform rolling restart of backend containers.
   - Deprecate old database user credentials once backend readiness probes report `UP`.
