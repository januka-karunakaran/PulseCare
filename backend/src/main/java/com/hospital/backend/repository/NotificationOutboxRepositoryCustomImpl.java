package com.hospital.backend.repository;

import com.hospital.backend.enums.OutboxStatus;
import com.hospital.backend.model.NotificationOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class NotificationOutboxRepositoryCustomImpl implements NotificationOutboxRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public Optional<NotificationOutbox> claimNextEligible(String workerId, int leaseSeconds, Instant now) {
        Criteria readyCriteria = new Criteria().andOperator(
                Criteria.where("status").in(List.of(OutboxStatus.PENDING.name(), OutboxStatus.RETRY.name())),
                Criteria.where("nextAttemptAt").lte(now)
        );

        Criteria staleLeaseCriteria = new Criteria().andOperator(
                Criteria.where("status").is(OutboxStatus.PROCESSING.name()),
                Criteria.where("claimUntil").lt(now)
        );

        Query query = new Query(new Criteria().orOperator(readyCriteria, staleLeaseCriteria));
        query.with(Sort.by(Sort.Direction.ASC, "nextAttemptAt"));

        Update update = new Update()
                .set("status", OutboxStatus.PROCESSING.name())
                .set("claimedBy", workerId)
                .set("claimUntil", now.plusSeconds(leaseSeconds))
                .set("updatedAt", now);

        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        NotificationOutbox claimed = mongoTemplate.findAndModify(query, update, options, NotificationOutbox.class);

        return Optional.ofNullable(claimed);
    }

    @Override
    public long cancelPendingForAggregate(String aggregateId, Instant now) {
        Query query = new Query(Criteria.where("aggregateId").is(aggregateId)
                .and("status").in(List.of(OutboxStatus.PENDING.name(), OutboxStatus.RETRY.name(), OutboxStatus.PENDING, OutboxStatus.RETRY)));

        Update update = new Update()
                .set("status", OutboxStatus.CANCELLED.name())
                .set("updatedAt", now)
                .set("lastErrorMessage", "Cancelled due to aggregate status change");

        return mongoTemplate.updateMulti(query, update, NotificationOutbox.class).getModifiedCount();
    }

    @Override
    public long cancelPendingByAggregateId(String aggregateId) {
        return cancelPendingForAggregate(aggregateId, Instant.now());
    }

    @Override
    public List<NotificationOutbox> findByAggregateIdAndStatusIn(String aggregateId, List<OutboxStatus> statuses) {
        List<String> statusNames = statuses.stream().map(Enum::name).collect(Collectors.toList());
        Query query = new Query(Criteria.where("aggregateId").is(aggregateId)
                .and("status").in(statusNames));
        return mongoTemplate.find(query, NotificationOutbox.class);
    }
}
