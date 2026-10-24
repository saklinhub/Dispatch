package com.dispatch.service;

import com.dispatch.dto.request.SaveEmailCredentialRequest;
import com.dispatch.dto.request.SaveSmsCredentialRequest;
import com.dispatch.dto.response.DeveloperCredentialResponse;
import com.dispatch.entity.DeveloperChannelCredential;
import com.dispatch.entity.enums.NotificationType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeveloperCredentialService {

    DeveloperCredentialResponse saveEmailCredentials(UUID userId, SaveEmailCredentialRequest request);

    DeveloperCredentialResponse saveSmsCredentials(UUID userId, SaveSmsCredentialRequest request);

    List<DeveloperCredentialResponse> getCredentials(UUID userId);

    void deleteCredential(UUID userId, NotificationType channel);

    Optional<DeveloperChannelCredential> findActiveCredential(UUID userId, NotificationType channel);
}
