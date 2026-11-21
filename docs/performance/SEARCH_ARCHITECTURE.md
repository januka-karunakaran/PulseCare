# PulseCare Search Architecture & Pipeline Specification

## 1. Architectural Overview

PulseCare 2.0 uses database-side query composition and aggregation to eliminate all in-memory collection scans and $O(N)$ query loops.

```
+-----------------------------------------------------------------------------------------+
|                        FEATURE 02.2 OPTIMIZED SEARCH ARCHITECTURE                       |
|                                                                                         |
|  [Client] ---> [SearchController] ---> [SearchServiceImpl]                              |
|                                            |                                            |
|                  1. Sanitize & Escape Regex (Pattern.quote)                             |
|                  2. Role & Ownership Authorization Bounds                               |
|                  3. Build Aggregation / Criteria Pipeline                               |
|                                            |                                            |
|                                            v                                            |
|                                   [MongoDB Engine]                                      |
|                                                                                         |
|      +---------------------------------------------------------------------------+      |
|      | Stage 1: $match role=DOCTOR, name/spec regex                              |      |
|      | Stage 2: $lookup doctors on userId or _id (sub-pipeline with $limit 1)    |      |
|      | Stage 3: $unwind doctorProfile (preserveNullAndEmptyArrays: true)         |      |
|      | Stage 4: $match rating >= minRating (if requested)                        |      |
|      | Stage 5: $sort by rating / name / spec / experience                       |      |
|      | Stage 6: $facet { totalCount: [$count], data: [$skip, $limit] }           |      |
|      +---------------------------------------------------------------------------+      |
|                                            |                                            |
|                  Paginated Doctor Slice returned (<= 100 records)                       |
|                                            v                                            |
|                                   [SearchServiceImpl]                                   |
|                                            |                                            |
|      +---------------------------------------------------------------------------+      |
|      | Stage 7: Collect page doctor IDs (canonical profile ID + user ID)         |      |
|      | Stage 8: Aggregate appointments: $match doctorId $in IDs                  |      |
|      |          $group by doctorId -> totalCount, monthlyCount                   |      |
|      | Stage 9: Sum counts across aliases without duplicate counting             |      |
|      | Stage 10: Omit unapproved private fields (phone, license)                 |      |
|      +---------------------------------------------------------------------------+      |
|                                            |                                            |
|                                            v                                            |
|                                 [SearchResultsWrapper]                                  |
+-----------------------------------------------------------------------------------------+
```

---

## 2. Doctor Identity Mapping Specification
- A doctor user has an account in the `users` collection (`_id`: user ID, `role`: `"DOCTOR"`).
- The doctor's professional profile resides in the `doctors` collection (`_id`: profile ID, `userId`: reference to user ID).
- In legacy data, `doctor._id` may equal `user._id`. In canonical architecture, `doctor._id` is distinct from `user._id` (`Doctor.id != Doctor.userId`).
- The aggregation lookup joins `doctors` where either:
  `$eq: ["$userId", "$$uid"]` OR `$eq: ["$_id", "$$uid"]`.
- The returned `SearchDoctorResponse.doctorId` is set to the canonical doctor profile ID (`profile._id`), falling back to user ID only if no separate profile exists.
- In appointment booking, the service accepts both profile ID and user ID interchangeably, ensuring backward compatibility.

---

## 3. Scalable Appointment Counting Specification
- Instead of loading all appointment documents into Java memory, a MongoDB aggregation on `appointments` executes:
  ```json
  [
    { "$match": { "doctorId": { "$in": pageDoctorIds } } },
    {
      "$group": {
        "_id": "$doctorId",
        "totalCount": { "$sum": 1 },
        "monthlyCount": {
          "$sum": {
            "$cond": [
              {
                "$and": [
                  { "$gte": ["$appointmentDate", startOfMonth] },
                  { "$lte": ["$appointmentDate", endOfMonth] }
                ]
              },
              1,
              0
            ]
          }
        }
      }
    }
  ]
  ```
- Returned summary documents are mapped in memory. For each doctor on the page:
  `totalCount = totalMap.getOrDefault(userId, 0L) + (profileId != null && !profileId.equals(userId) ? totalMap.getOrDefault(profileId, 0L) : 0L)`.
  This ensures zero duplicate counting while supporting both canonical and legacy appointment references.
