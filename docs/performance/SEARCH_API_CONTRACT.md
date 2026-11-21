# PulseCare Search & Filter API Contract

## 1. Search Doctors
- **Endpoint:** `GET /api/search/doctors`
- **Access:** Public / Authenticated
- **Query Parameters:**
  - `searchTerm` (optional, string): Case-insensitive regex match against `fullName` or `email`.
  - `specialization` (optional, string): Exact or prefix match against doctor specialization.
  - `minRating` (optional, number): Lower bound for rating filter (`0.0` to `5.0`).
  - `limit` (optional, integer, default: `20`, max: `100`): Results per page.
  - `offset` (optional, integer, default: `0`): Pagination offset ($>= 0$).
  - `sortBy` (optional, string, default: `fullName`): Allowed values: `fullName`, `specialization`, `rating`.
  - `sortDir` (optional, string, default: `asc`): `asc` or `desc`.
- **Response Format:**
```json
{
  "data": [
    {
      "id": "doc-uuid",
      "userId": "user-uuid",
      "fullName": "Dr. Sarah Connor",
      "email": "sarah.connor@pulsecare.com",
      "specialization": "Cardiology",
      "rating": 4.9,
      "totalAppointments": 142,
      "completedAppointments": 138,
      "availableDays": ["Monday", "Wednesday", "Friday"],
      "consultationFee": 150.0
    }
  ],
  "totalCount": 1,
  "pageNumber": 0,
  "pageSize": 20,
  "totalPages": 1,
  "hasMore": false
}
```

---

## 2. Search Patients
- **Endpoint:** `GET /api/search/patients`
- **Access:** Restricted (Doctors and Admins only; Patients receive 403 Forbidden).
- **Query Parameters:**
  - `searchTerm` (required, string): Substring match against `fullName`, `email`, or `phoneNumber`.
  - `limit` (optional, integer, default: `20`, max: `100`): Results per page.
  - `offset` (optional, integer, default: `0`): Pagination offset ($>= 0$).
- **Response Format:**
```json
{
  "data": [
    {
      "id": "pat-uuid",
      "fullName": "John Doe",
      "email": "john.doe@example.com",
      "phoneNumber": "+1234567890",
      "role": "PATIENT"
    }
  ],
  "totalCount": 1,
  "pageNumber": 0,
  "pageSize": 20,
  "totalPages": 1,
  "hasMore": false
}
```

---

## 3. Filter Appointments
- **Endpoint:** `POST /api/search/appointments`
- **Access:** Authenticated (Role-scoped: Patients restricted to own records; Doctors restricted to their schedules; Admins unrestricted).
- **Request Body:**
```json
{
  "doctorId": "optional-doctor-id",
  "patientId": "optional-patient-id",
  "startDate": "2026-01-01",
  "endDate": "2026-12-31",
  "status": "COMPLETED",
  "reason": "Routine Checkup",
  "limit": 20,
  "offset": 0,
  "sortBy": "appointmentDate",
  "sortDir": "desc"
}
```
- **Response Format:**
```json
{
  "data": [
    {
      "appointmentId": "appt-uuid",
      "doctorName": "Dr. Sarah Connor",
      "patientName": "John Doe",
      "appointmentDate": "2026-11-20",
      "timeSlot": "10:00 AM",
      "status": "COMPLETED",
      "reason": "Routine Checkup",
      "fee": 150.0
    }
  ],
  "totalCount": 1,
  "pageNumber": 0,
  "pageSize": 20,
  "totalPages": 1,
  "hasMore": false
}
```

---

## 4. Filter Prescriptions
- **Endpoint:** `POST /api/search/prescriptions`
- **Access:** Authenticated (Role-scoped).
- **Request Body:**
```json
{
  "doctorId": "optional-doctor-id",
  "patientId": "optional-patient-id",
  "startDate": "2026-01-01",
  "endDate": "2026-12-31",
  "medicine": "Amoxicillin",
  "status": "ACTIVE",
  "limit": 20,
  "offset": 0,
  "sortBy": "createdAt",
  "sortDir": "desc"
}
```
- **Response Format:**
```json
{
  "data": [
    {
      "prescriptionId": "presc-uuid",
      "doctorName": "Dr. Sarah Connor",
      "patientName": "John Doe",
      "medicines": ["Amoxicillin 500mg", "Paracetamol"],
      "diagnosis": "Bacterial Infection",
      "status": "ACTIVE",
      "createdAt": "2026-11-20T10:00:00"
    }
  ],
  "totalCount": 1,
  "pageNumber": 0,
  "pageSize": 20,
  "totalPages": 1,
  "hasMore": false
}
```
