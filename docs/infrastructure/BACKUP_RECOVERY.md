# Database Backup & Disaster Recovery Runbook

## 1. Objectives & Metrics
- **Recovery Point Objective (RPO):** Maximum 1 hour of acceptable data loss.
- **Recovery Time Objective (RTO):** Maximum 15 minutes to restore full operational state.

---

## 2. Backup Strategy

### 2.1 Backup Methodology
- Backups utilize native MongoDB utilities: `mongodump` with `--gzip` compression and archive mode.
- Output archives are timestamped and encrypted using AES-256 (`gpg --symmetric`).

### 2.2 Scheduled Automated Backup (Cron)
A cron job runs hourly on the database host or dedicated backup sidecar container:
```bash
#!/bin/bash
set -euo pipefail

BACKUP_DIR="/var/backups/mongodb"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/hms_backup_${TIMESTAMP}.archive.gz"
ENCRYPTED_FILE="${BACKUP_FILE}.enc"

mkdir -p "${BACKUP_DIR}"

# Execute consistent dump
mongodump \
  --uri="${MONGODB_URI}" \
  --archive="${BACKUP_FILE}" \
  --gzip

# Encrypt backup
gpg --batch --yes --passphrase-file /etc/backup/passphrase \
  --symmetric --cipher-algo AES256 \
  -o "${ENCRYPTED_FILE}" "${BACKUP_FILE}"

rm -f "${BACKUP_FILE}"

# Retention policy: Prune local backups older than 30 days
find "${BACKUP_DIR}" -type f -name "*.enc" -mtime +30 -delete

echo "Backup ${ENCRYPTED_FILE} completed successfully."
```

---

## 3. Disaster Recovery & Restoration Procedure

### 3.1 Prerequisites
- Access to the target MongoDB cluster.
- Decryption passphrase.
- Target backup archive.

### 3.2 Restoration Steps
1. **Decrypt the Backup File:**
   ```bash
   gpg --batch --passphrase-file /etc/backup/passphrase \
     --decrypt -o /tmp/restore.archive.gz /var/backups/mongodb/hms_backup_YYYYMMDD_HHMMSS.archive.gz.enc
   ```

2. **Restore with `--drop` Protection:**
   The `--drop` flag replaces collections with the backup state:
   ```bash
   mongorestore \
     --uri="${MONGODB_URI}" \
     --archive=/tmp/restore.archive.gz \
     --gzip \
     --drop
   ```

3. **Secure Cleanup:**
   ```bash
   shred -u /tmp/restore.archive.gz
   ```

4. **Verify Application Readiness:**
   ```bash
   curl -f http://localhost:8080/api/health/readiness
   ```

---

## 4. Periodic Backup Verification & Drills
- **Monthly Drill:** Restore the latest backup into an isolated staging environment.
- Verify patient record count, appointment consistency, and index existence.
- Confirm that patient medical records and encryption integrity are fully preserved.
