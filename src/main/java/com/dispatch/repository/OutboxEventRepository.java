package com.dispatch.repository;

import com.dispatch.entity.OutboxEvent;
import com.dispatch.entity.enums.OutboxEventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status, Pageable pageable);

    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxEventStatus status);

    List<OutboxEvent> findByAggregateIdOrderByCreatedAtAsc(UUID aggregateId);

    long countByStatus(OutboxEventStatus status);

    @Query("SELECT e FROM OutboxEvent e WHERE e.status = :status ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingEventsBatch(@Param("status") OutboxEventStatus status, Pageable pageable);
}
