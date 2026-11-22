package com.hospital.backend.repository;

import com.hospital.backend.model.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {

    Optional<Notification> findBySourceOutboxId(String sourceOutboxId);

    boolean existsBySourceOutboxId(String sourceOutboxId);

    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(String userId);

    long countByUserIdAndIsReadFalse(String userId);

    long countByUserId(String userId);

    void deleteByUserId(String userId);
}
