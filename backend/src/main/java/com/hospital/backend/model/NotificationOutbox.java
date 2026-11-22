package com.hospital.backend.model;

import com.hospital.backend.enums.OutboxChannel;
import com.hospital.backend.enums.OutboxStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * MongoDB-backed Durable Notification Outbox Entity.
 * Guarantees at-least-once processing, atomic leasing, bounded exponential retries,
 * and deterministic deduplication.
 */
@Document(collection = "notification_outbox")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "outbox_status_next_attempt_idx", def = "{'status': 1, 'nextAttemptAt': 1}"),
        @CompoundIndex(name = "outbox_status_claim_until_idx", def = "{'status': 1, 'claimUntil': 1}"),
        @CompoundIndex(name = "outbox_aggregate_status_idx", def = "{'aggregateId': 1, 'status': 1}"),
        @CompoundIndex(name = "outbox_recipient_created_idx", def = "{'recipientUserId': 1, 'createdAt': -1}")
})
public class NotificationOutbox {

    @Id
    private String id;

    private String eventId;

    private String eventType;

    private String aggregateId; // Appointment ID or Prescription ID

    @Indexed
    private String recipientUserId;

    private String recipientEmail;

    private String recipientRole; // PATIENT or DOCTOR

    private OutboxChannel channel; // IN_APP or EMAIL

    private int payloadVersion;

    private OutboxStatus status; // PENDING, PROCESSING, DELIVERED, RETRY, FAILED, CANCELLED

    private int attemptCount;

    private int maxAttempts;

    private Instant nextAttemptAt;

    private Instant createdAt;

    private Instant updatedAt;

    private String claimedBy;

    private Instant claimUntil;

    private String lastErrorCode;

    private String lastErrorMessage;

    private Instant deliveredAt;

    @Indexed(unique = true)
    private String deduplicationKey;

    private String title;

    private String message;

    private String actionUrl;

    private Map<String, String> metadata;
}
