# Laboratory Data Model & Schema Specification

## Collections

### 1. `lab_test_definitions`
```json
{
  "_id": "ObjectId",
  "testCode": "CBC",
  "testName": "Complete Blood Count",
  "category": "Hematology",
  "description": "Standard diagnostic blood screen",
  "specimenType": "Whole Blood",
  "sampleVolume": "3 mL",
  "collectionInstructions": "EDTA lavender top tube",
  "defaultUnit": "10^3/uL",
  "referenceRangeMetadata": "4.5 - 11.0",
  "active": true,
  "createdAt": "ISODate",
  "updatedAt": "ISODate",
  "version": 0
}
```

### 2. `lab_orders`
```json
{
  "_id": "ObjectId",
  "orderNumber": "ORD-1795407158931-7E27",
  "patientId": "patient-123",
  "doctorId": "doctor-456",
  "encounterId": "enc-789",
  "orderedTests": [
    {
      "testCode": "CBC",
      "testName": "Complete Blood Count",
      "category": "Hematology",
      "specimenType": "Whole Blood",
      "defaultUnit": "10^3/uL",
      "referenceRange": "4.5 - 11.0"
    }
  ],
  "priority": "ROUTINE",
  "clinicalNotes": "Annual wellness checkup",
  "status": "ORDERED",
  "orderedAt": "ISODate",
  "orderedByUserId": "user-456",
  "createdAt": "ISODate",
  "updatedAt": "ISODate",
  "version": 0
}
```

### 3. `lab_specimens`
```json
{
  "_id": "ObjectId",
  "specimenNumber": "SPEC-1795407158931-89AB",
  "labOrderId": "order-123",
  "patientId": "patient-123",
  "specimenType": "Whole Blood",
  "collectionStatus": "COLLECTED",
  "collectedAt": "ISODate",
  "collectedByUserId": "tech-user-1",
  "notes": "Left arm venipuncture",
  "createdAt": "ISODate",
  "updatedAt": "ISODate",
  "version": 0
}
```

### 4. `lab_results`
```json
{
  "_id": "ObjectId",
  "labOrderId": "order-123",
  "specimenId": "spec-123",
  "patientId": "patient-123",
  "doctorId": "doctor-456",
  "testCode": "CBC",
  "testName": "Complete Blood Count",
  "resultType": "NUMERIC",
  "numericValue": 7.5,
  "unit": "10^3/uL",
  "referenceRange": "4.5 - 11.0",
  "interpretation": "NORMAL",
  "status": "NORMAL",
  "workflowStatus": "RELEASED",
  "measuredAt": "ISODate",
  "enteredByUserId": "tech-user-1",
  "verifiedByUserId": "doc-user-1",
  "verifiedAt": "ISODate",
  "releasedByUserId": "doc-user-1",
  "releasedAt": "ISODate",
  "revision": 1,
  "version": 0
}
```
