package com.dispatch.repository;

import com.dispatch.entity.DeliveryAttempt;
import com.dispatch.entity.enums.AttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {

    List<DeliveryAttempt> findByNotificationIdOrderByAttemptNumberAsc(UUID notificationId);

    Optional<DeliveryAttempt> findTopByNotificationIdOrderByAttemptNumberDesc(UUID notificationId);

    long countByNotificationId(UUID notificationId);

    List<DeliveryAttempt> findByStatusAndNextRetryAtBefore(AttemptStatus status, Instant now);
}
