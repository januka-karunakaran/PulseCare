# Clinical Data Model Specification

## Collections & Schemas

### 1. `clinical_encounters`
Stores the metadata and state of each consultation encounter:
- `id`: Unique identifier (String)
- `appointmentId`: Linked appointment ID
- `patientId`: Canonical patient ID
- `doctorId`: Canonical doctor ID
- `encounterType`: `OUTPATIENT`, `INPATIENT`, `EMERGENCY`, `FOLLOW_UP`, `ROUTINE_CHECKUP`
- `status`: `DRAFT`, `IN_PROGRESS`, `SIGNED`, `AMENDED`, `CANCELLED`
- `visitStartedAt`: UTC timestamp
- `visitCompletedAt`: UTC timestamp
- `chiefComplaint`: Patient's primary presenting concern
- `historyOfPresentIllness`: Narrative history
- `clinicalSummary`: Overall encounter summary
- `signedAt`: UTC timestamp of signing
- `signedByUserId`: Doctor User ID who signed
- `signedByDoctorName`: Name of doctor at signing
- `version`: `@Version` for optimistic concurrency locking

**Compound Indexes:**
- `encounter_patient_started_idx`: `{'patientId': 1, 'visitStartedAt': -1}`
- `encounter_doc_status_idx`: `{'doctorId': 1, 'status': 1}`
- `encounter_appt_idx`: `{'appointmentId': 1}`

---

### 2. `clinical_note_revisions`
Stores SOAP consultation notes with append-only revisions:
- `id`: Unique identifier (String)
- `encounterId`: Linked encounter
- `patientId`: Linked patient
- `doctorId`: Author doctor
- `revisionNumber`: `1` for initial signed note, `2+` for amendments
- `status`: `DRAFT`, `SIGNED`, `AMENDED`
- **Subjective (S):** `chiefComplaint`, `subjective`
- **Objective (O):** `objective`, `vitalsSummary`
- **Assessment (A):** `assessment`, `diagnosisText`, `diagnosisCode`, `codingSystem`, `diagnosisStatus`
- **Plan (P):** `plan`, `testsRequested`, `followUpInstructions`, `followUpDate`, `followUpRequired`, `referralNotes`
- **Amendment Metadata:** `amendmentReason`, `amendedByUserId`, `amendedAt`
- **Signature Metadata:** `signedByUserId`, `signedByDoctorName`, `signedAt`
- `version`: `@Version` optimistic locking

---

### 3. `clinical_observations`
Stores structured quantitative vital signs:
- `systolicBp`, `diastolicBp`: mmHg (40-300 / 20-200)
- `heartRate`: bpm (20-300)
- `temperature`: °C (30.0-45.0)
- `respiratoryRate`: breaths/min (5-100)
- `oxygenSaturation`: % (50-100)
- `weightKg`, `heightCm`, `painScore` (0-10)
- `observedAt`: UTC Instant
- `recordedByUserId`, `recordedByName`

---

### 4. `patient_clinical_profiles`
Maintains long-term medical background separate from basic demographics:
- `patientId`: Unique indexed ID
- `allergyStatus`: `UNKNOWN`, `NO_KNOWN_ALLERGIES`, `CONFIRMED_ALLERGIES`
- `allergies`: Array of `AllergyRecord` (`allergen`, `reaction`, `severity`, `verificationStatus`)
- `pastMedicalHistory`: List of confirmed past conditions
- `pastSurgicalHistory`: List of past surgeries
- `chronicConditions`: List of ongoing conditions
- `familyMedicalHistory`, `socialHistory`, `currentMedications`

---

### 5. `clinical_audit_logs`
Immutable audit trail:
- `actorUserId`, `actorEmail`, `actorRole`
- `patientId`, `resourceType`, `resourceId`, `action`, `occurredAt`, `correlationId`, `outcome`, `details`
