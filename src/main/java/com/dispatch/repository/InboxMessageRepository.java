package com.dispatch.repository;

import com.dispatch.entity.InboxMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InboxMessageRepository extends JpaRepository<InboxMessage, UUID> {

    Page<InboxMessage> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<InboxMessage> findByUserIdAndReadOrderByCreatedAtDesc(UUID userId, boolean read, Pageable pageable);

    long countByUserIdAndReadFalse(UUID userId);

    Optional<InboxMessage> findByIdAndUserId(UUID id, UUID userId);

    List<InboxMessage> findByUserIdAndReadFalse(UUID userId);

    @Modifying
    @Query("UPDATE InboxMessage m SET m.read = true, m.readAt = :readAt WHERE m.user.id = :userId AND m.read = false")
    int markAllAsReadForUser(@Param("userId") UUID userId, @Param("readAt") Instant readAt);
}
