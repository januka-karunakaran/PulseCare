package com.hospital.backend.repository;

import com.hospital.backend.model.NotificationOutbox;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationOutboxRepository extends MongoRepository<NotificationOutbox, String>, NotificationOutboxRepositoryCustom {

    Optional<NotificationOutbox> findByDeduplicationKey(String deduplicationKey);

    boolean existsByDeduplicationKey(String deduplicationKey);
}
