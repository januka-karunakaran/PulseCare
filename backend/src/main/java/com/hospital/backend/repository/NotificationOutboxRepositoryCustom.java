package com.hospital.backend.repository;

import com.hospital.backend.enums.OutboxStatus;
import com.hospital.backend.model.NotificationOutbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationOutboxRepositoryCustom {

    /**
     * Atomically claims the next eligible outbox item using findAndModify.
     * Handles both ready PENDING/RETRY records and stale worker leases where claimUntil < now.
     */
    Optional<NotificationOutbox> claimNextEligible(String workerId, int leaseSeconds, Instant now);

    /**
     * Cancels any pending or retry outbox records for a given aggregate (e.g. cancelled appointment).
     */
    long cancelPendingForAggregate(String aggregateId, Instant now);

    /**
     * Convenience method to cancel pending or retry outbox records for an aggregate.
     */
    long cancelPendingByAggregateId(String aggregateId);

    /**
     * Finds outbox records for an aggregate in given statuses.
     */
    List<NotificationOutbox> findByAggregateIdAndStatusIn(String aggregateId, List<OutboxStatus> statuses);
}
