package com.dispatch.repository;

import com.dispatch.entity.DeveloperChannelCredential;
import com.dispatch.entity.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeveloperChannelCredentialRepository extends JpaRepository<DeveloperChannelCredential, UUID> {

    Optional<DeveloperChannelCredential> findByUserIdAndChannelAndIsActiveTrue(UUID userId, NotificationType channel);

    Optional<DeveloperChannelCredential> findByUserIdAndChannel(UUID userId, NotificationType channel);

    List<DeveloperChannelCredential> findByUserId(UUID userId);

    void deleteByUserIdAndChannel(UUID userId, NotificationType channel);
}
