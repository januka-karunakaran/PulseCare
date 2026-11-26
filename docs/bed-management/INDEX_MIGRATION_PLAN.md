# MongoDB Index Migration Plan for Inpatient Operations

## Recommended Indexes
### Collection: `hospital_wards`
```javascript
db.hospital_wards.createIndex({ "wardCode": 1 }, { unique: true });
```

### Collection: `beds`
```javascript
db.beds.createIndex({ "ward": 1, "bedNumber": 1 }, { unique: true });
db.beds.createIndex({ "status": 1, "wardId": 1 });
db.beds.createIndex({ "type": 1, "status": 1 });
```

### Collection: `bed_bookings`
```javascript
db.bed_bookings.createIndex({ "admissionNumber": 1 }, { unique: true, sparse: true });
db.bed_bookings.createIndex({ "patientId": 1, "admittedAt": -1 });
db.bed_bookings.createIndex({ "doctorId": 1, "admittedAt": -1 });
db.bed_bookings.createIndex({ "bedId": 1, "status": 1 });
db.bed_bookings.createIndex({ "status": 1, "admittedAt": -1 });
```

### Collection: `bed_occupancy_events`
```javascript
db.bed_occupancy_events.createIndex({ "eventId": 1 }, { unique: true });
db.bed_occupancy_events.createIndex({ "bedId": 1, "occurredAt": 1 });
db.bed_occupancy_events.createIndex({ "admissionId": 1, "sequenceNumber": 1 });
db.bed_occupancy_events.createIndex({ "wardId": 1, "occurredAt": 1 });
db.bed_occupancy_events.createIndex({ "patientId": 1, "occurredAt": 1 });
```

### Collection: `bed_transfers`
```javascript
db.bed_transfers.createIndex({ "transferNumber": 1 }, { unique: true });
db.bed_transfers.createIndex({ "admissionId": 1, "transferredAt": -1 });
db.bed_transfers.createIndex({ "patientId": 1, "transferredAt": -1 });
```
