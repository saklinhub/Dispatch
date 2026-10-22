package com.dispatch.repository;

import com.dispatch.entity.DeliveryAttempt;
import com.dispatch.entity.enums.AttemptStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query(value = "SELECT da FROM DeliveryAttempt da JOIN FETCH da.notification n WHERE n.user.id = :userId",
           countQuery = "SELECT COUNT(da) FROM DeliveryAttempt da JOIN da.notification n WHERE n.user.id = :userId")
    Page<DeliveryAttempt> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = "SELECT da FROM DeliveryAttempt da JOIN FETCH da.notification n WHERE n.user.id = :userId AND da.status = :status",
           countQuery = "SELECT COUNT(da) FROM DeliveryAttempt da JOIN da.notification n WHERE n.user.id = :userId AND da.status = :status")
    Page<DeliveryAttempt> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") AttemptStatus status, Pageable pageable);
}
