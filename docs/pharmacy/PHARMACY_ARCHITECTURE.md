# Pharmacy Subsystem Architecture

## Architectural Overview
The Pharmacy subsystem integrates seamlessly into PulseCare 2.0's Spring Boot / MongoDB core while maintaining strict modular separation.

```
+-----------------------------------------------------------------------------------+
|                              PulseCare Client Layers                              |
|   Doctor Workspace     |   Pharmacist Workspaces   |   Patient Medical History   |
+-----------------------------------------------------------------------------------+
                                         | REST / HTTPS
                                         v
+-----------------------------------------------------------------------------------+
|                        Spring Security & Authorization Filter                     |
|           Role-Based Access (ADMIN, PHARMACIST, DOCTOR, PATIENT)                  |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|                          PharmacyController (/api/pharmacy)                       |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|                              PharmacyServiceImpl                                  |
|   - Catalog Management     - FEFO Algorithm        - Atomic Stock Allocation      |
|   - Prescription Queue     - Return Quarantining   - Audit & Alerts               |
+-----------------------------------------------------------------------------------+
          |                      |                      |                     |
          v                      v                      v                     v
+-------------------+  +-------------------+  +-------------------+  +--------------+
| PharmacyMedicine  |  | PharmacyStockBatch|  | PharmacyDispense  |  | StockMovement|
|    Collection     |  |    Collection     |  |    Collection     |  |    Ledger    |
+-------------------+  +-------------------+  +-------------------+  +--------------+
```

## Core Tenets
1. **Human Clinical Invariance:** Only licensed pharmacists and administrators may allocate or dispense medication.
2. **Deterministic FEFO:** Allocations prioritize the earliest expiring usable batch.
3. **Storage Segregation:** Expired or returned medications never enter the saleable pool.
4. **Append-Only Auditing:** Every movement creates a permanent ledger row.
