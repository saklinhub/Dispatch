package com.dispatch.repository;

import com.dispatch.entity.IdempotencyRecord;
import com.dispatch.entity.enums.IdempotencyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    @org.springframework.transaction.annotation.Transactional
    void deleteByIdempotencyKey(String idempotencyKey);

    @org.springframework.transaction.annotation.Transactional
    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.createdAt < :cutoff")
    int deleteExpiredRecords(@Param("cutoff") Instant cutoff);

    @Query("SELECT COUNT(r) FROM IdempotencyRecord r WHERE r.status = :status")
    long countByStatus(@Param("status") IdempotencyStatus status);
}
