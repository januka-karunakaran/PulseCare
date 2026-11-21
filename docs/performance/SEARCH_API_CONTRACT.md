# PulseCare Search & Filter API Contract

## 1. Search Doctors
- **Endpoint:** `GET /api/search/doctors`
- **Access:** Public / Authenticated
- **Query Parameters:**
  - `searchTerm` (optional, string): Case-insensitive regex match against `fullName` or `specialization`.
  - `specialization` (optional, string): Exact or prefix match against doctor specialization.
  - `minRating` (optional, number): Lower bound for rating filter (`0.0` to `5.0`).
  - `limit` (optional, integer, default: `20`, max: `100`): Results per page.
  - `offset` (optional, integer, default: `0`): Pagination offset ($>= 0$).
  - `sortBy` (optional, string, default: `fullName`): Allowed values: `name`, `specialization`, `rating`, `experience`, `appointments`.
  - `sortOrder` (optional, string, default: `asc`): `asc` or `desc`.
- **Response Format:**
```json
{
  "data": [
    {
      "doctorId": "doc-profile-uuid",
      "name": "Dr. Sarah Connor",
      "email": "sarah.connor@pulsecare.com",
      "specialization": "Cardiology",
      "avgRating": 4.9,
      "totalRatings": 142,
      "totalAppointments": 138,
      "appointmentsThisMonth": 24,
      "photoUrl": "https://...",
      "bio": "Cardiologist with 15 years experience..."
    }
  ],
  "totalCount": 1,
  "pageNumber": 1,
  "limit": 20,
  "offset": 0,
  "totalPages": 1,
  "hasMore": false
}
```
*Note: Sensitive staff fields (`phoneNumber`, `licenseNumber`) are omitted from public directory responses to protect doctor privacy.*

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
  "pageNumber": 1,
  "limit": 20,
  "offset": 0,
  "totalPages": 1,
  "hasMore": false
}
```
*Clinical Restriction: Doctors are restricted to searching only patients with an active or completed clinical relationship (`status != "CANCELLED"`).*

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
  "status": "CONFIRMED",
  "reason": "Routine Checkup",
  "limit": 20,
  "offset": 0,
  "sortBy": "date",
  "sortOrder": "desc"
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
      "appointmentTime": "10:00 AM",
      "status": "CONFIRMED",
      "reason": "Routine Checkup",
      "doctorId": "doc-profile-uuid",
      "patientId": "patient-uuid"
    }
  ],
  "totalCount": 1,
  "pageNumber": 1,
  "limit": 20,
  "offset": 0,
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
  "sortBy": "date",
  "sortOrder": "desc"
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
      "medicines": "Amoxicillin 500mg, Paracetamol",
      "dosageInstructions": "1 tablet twice daily after meals",
      "status": "ACTIVE",
      "createdDate": "2026-11-20T10:00:00",
      "doctorId": "doc-profile-uuid",
      "patientId": "patient-uuid"
    }
  ],
  "totalCount": 1,
  "pageNumber": 1,
  "limit": 20,
  "offset": 0,
  "totalPages": 1,
  "hasMore": false
}
```
