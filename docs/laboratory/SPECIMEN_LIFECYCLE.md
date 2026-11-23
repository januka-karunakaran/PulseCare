# Specimen Collection & Tracking Lifecycle

## Specimen State Machine
```mermaid
stateDiagram-v2
    [*] --> PENDING: Order created
    PENDING --> COLLECTED: Phlebotomist/Staff collects sample
    COLLECTED --> RECEIVED: Lab intake confirms accession
    COLLECTED --> REJECTED: Sample hemolyzed/insufficient volume
    RECEIVED --> IN_PROCESS: Analysis started
    IN_PROCESS --> COMPLETED: Measurement complete
    REJECTED --> [*]
    COMPLETED --> [*]
```

## Security & Identification Controls
- **Accession Identifiers:** Every sample receives a server-generated unique identifier (`SPEC-timestamp-XXXX`).
- **Separation of Collection & Receipt:** Phlebotomy/collection and laboratory intake are tracked independently with user IDs and timestamps.
- **Rejection Tracking:** Rejections require an explicit clinical reason (e.g. hemolyzed, clotted, incorrect container).
- **Patient Safety:** A rejected specimen is never allowed to produce released results.
