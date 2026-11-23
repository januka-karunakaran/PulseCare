# Laboratory MongoDB Index & Schema Migration Plan

## Indexes Created

### 1. `lab_test_definitions`
- Unique index on `testCode`: ensures catalog code uniqueness.
- Compound index on `{'active': 1, 'category': 1}`: optimizes catalog browsing.

### 2. `lab_orders`
- Unique index on `orderNumber`: prevents duplicate order numbers.
- Compound index on `{'patientId': 1, 'orderedAt': -1}`: optimizes patient order timeline queries.
- Compound index on `{'doctorId': 1, 'orderedAt': -1}`: optimizes physician work queues.
- Compound index on `{'status': 1, 'priority': 1}`: optimizes STAT and urgent order triage.

### 3. `lab_specimens`
- Unique index on `specimenNumber`: ensures accession code uniqueness.
- Compound index on `{'labOrderId': 1, 'collectionStatus': 1}`: specimen lookup per order.

### 4. `lab_results`
- Compound index on `{'labOrderId': 1, 'testCode': 1}`: fast result retrieval per order item.
- Compound index on `{'patientId': 1, 'releasedAt': -1}`: patient portal history indexing.
- Compound index on `{'workflowStatus': 1, 'updatedAt': -1}`: verification queue indexing.

## Legacy Compatibility & Migration Strategy
- Existing records in `lab_results` retain all legacy fields (`status = NORMAL/ABNORMAL`, `testDate`, etc.).
- Historical records with `workflowStatus == null` are treated as legacy historical entries. They are safely rendered in patient medical history without modification.
- Under Feature 02.5, no automated bulk transformation is applied to historical documents, guaranteeing zero data corruption.
