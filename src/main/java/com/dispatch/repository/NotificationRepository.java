package com.dispatch.repository;

import com.dispatch.entity.Notification;
import com.dispatch.entity.enums.NotificationStatus;
import com.dispatch.entity.enums.NotificationType;
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
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByIdempotencyKey(String idempotencyKey);

    Page<Notification> findByUserId(UUID userId, Pageable pageable);

    Page<Notification> findByStatus(NotificationStatus status, Pageable pageable);

    Page<Notification> findByUserIdAndStatus(UUID userId, NotificationStatus status, Pageable pageable);

    Page<Notification> findByType(NotificationType type, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.status = :status AND n.scheduledAt IS NOT NULL AND n.scheduledAt <= :now")
    List<Notification> findDueScheduledNotifications(@Param("status") NotificationStatus status, @Param("now") Instant now);

    @Query("SELECT n FROM Notification n WHERE n.status = 'PROCESSING' AND n.updatedAt <= :threshold")
    List<Notification> findStuckProcessingNotifications(@Param("threshold") Instant threshold);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Notification n SET n.updatedAt = :updatedAt WHERE n.id = :id")
    void forceUpdateUpdatedAt(@Param("id") UUID id, @Param("updatedAt") Instant updatedAt);

    long countByStatus(NotificationStatus status);

    long countByUserIdAndStatus(UUID userId, NotificationStatus status);
}
