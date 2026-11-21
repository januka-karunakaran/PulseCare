# PulseCare Search Architecture & Query Processing Specification

## 1. Architectural Overview

Prior to Feature 02.2, PulseCare executed database searches by pulling entire collections from MongoDB into JVM memory using Spring Data repository `findAll()` or role-based finders, applying Java Streams for filtering, sorting, and pagination.

```
+------------------------------------------------------------------------+
|                          LEGACY ARCHITECTURE                           |
|                                                                        |
|  [Client] ---> [Controller] ---> [Service]                             |
|                                     |                                  |
|                                     v  findAll() (Entire Collection)   |
|                               [MongoDB Engine]                         |
|                                     |                                  |
|                        Huge BSON payload transferred                   |
|                                     v                                  |
|                         [JVM Heap / Memory]                            |
|                          - Stream.filter(...)                          |
|                          - Stream.sorted(...)                          |
|                          - Stream.skip().limit(...)                    |
|                          - Loop: 3 queries per doctor (N+1 storm!)     |
+------------------------------------------------------------------------+
```

### Optimized Feature 02.2 Architecture

```
+------------------------------------------------------------------------+
|                         FEATURE 02.2 ARCHITECTURE                      |
|                                                                        |
|  [Client] ---> [Controller] ---> [SearchServiceImpl]                   |
|                                     |                                  |
|                      1. Sanitize & Escaped Regex                       |
|                      2. Role & Ownership Authorization Bounds          |
|                      3. Dynamic Criteria Query Assembly                |
|                      4. Pageable & Sort at DB Level                    |
|                                     |                                  |
|                                     v                                  |
|                             [MongoDB Engine]                           |
|                      - Compound Index Scans                            |
|                      - Array Multikey Scans                            |
|                      - Count & Window Slicing                          |
|                                     |                                  |
|                   Small, paginated payload returned (<=100 records)    |
|                                     v                                  |
|                         [SearchServiceImpl]                            |
|                      - Batch ID Hydration (1 DB query for names)       |
|                                     v                                  |
|                            [Paginated Response]                        |
+------------------------------------------------------------------------+
```

---

## 2. Dynamic Query Pipeline

### Query Pipeline Stages
1. **Request Validation:**
   - Offset: validated $>= 0$.
   - Limit: defaulted to 20, constrained within $[1, 100]$.
   - Dates: parsed using strict `yyyy-MM-dd` formatter; verified that `startDate <= endDate`.
   - String Sanitization: all search terms wrapped with `Pattern.quote()`.
2. **Contextual Security Enclosure:**
   - Enforce caller identity via `AuthorizationService`.
   - Prevent IDOR: Patient role is restricted to own ID; Doctor role is restricted to their appointments/prescriptions.
   - For patient directory search, Patient role is rejected with HTTP 403 `AccessDeniedException`.
3. **Criteria Construction:**
   - MongoTemplate `Criteria` objects chained with `.andOperator()` or `.orOperator()`.
4. **Execution:**
   - `mongoTemplate.count()` determines exact total matching dataset size.
   - `mongoTemplate.find()` retrieves the single requested page slice.
5. **Batch Lookup:**
   - Unique patient and doctor IDs gathered across results.
   - Single `findAllById()` executes to hydrate display names.
