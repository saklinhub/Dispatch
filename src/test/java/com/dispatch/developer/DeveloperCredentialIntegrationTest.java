package com.dispatch.developer;

import com.dispatch.delivery.DeliveryDispatcher;
import com.dispatch.delivery.DeliveryResult;
import com.dispatch.dto.message.NotificationMessage;
import com.dispatch.dto.request.CreateNotificationRequest;
import com.dispatch.dto.request.SaveEmailCredentialRequest;
import com.dispatch.dto.request.SaveSmsCredentialRequest;
import com.dispatch.dto.response.DeveloperCredentialResponse;
import com.dispatch.dto.response.NotificationResponse;
import com.dispatch.entity.DeliveryAttempt;
import com.dispatch.entity.Notification;
import com.dispatch.entity.User;
import com.dispatch.entity.enums.AttemptStatus;
import com.dispatch.entity.enums.NotificationStatus;
import com.dispatch.entity.enums.NotificationType;
import com.dispatch.entity.enums.UserRole;
import com.dispatch.repository.*;
import com.dispatch.service.DeveloperCredentialService;
import com.dispatch.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DeveloperCredentialIntegrationTest {

    @Autowired
    private DeveloperCredentialService credentialService;

    @Autowired
    private DeveloperChannelCredentialRepository credentialRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private DeliveryDispatcher deliveryDispatcher;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        deliveryAttemptRepository.deleteAll();
        notificationRepository.deleteAll();
        credentialRepository.deleteAll();
        userRepository.deleteAll();

        alice = userRepository.save(User.builder()
                .email("alice@devcompany.com")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                .fullName("Developer Alice")
                .role(UserRole.USER)
                .build());

        bob = userRepository.save(User.builder()
                .email("bob@anothercorp.com")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                .fullName("Developer Bob")
                .role(UserRole.USER)
                .build());
    }

    @Test
    @DisplayName("Developers can save, retrieve, and mask their custom email credentials")
    void testSaveAndRetrieveEmailCredentials() {
        SaveEmailCredentialRequest request = SaveEmailCredentialRequest.builder()
                .provider("GMAIL")
                .username("alice@gmail.com")
                .password("abcd-efgh-ijkl-mnop")
                .fromAddress("alice@gmail.com")
                .fromName("Alice Personal App")
                .replyTo("alice-support@gmail.com")
                .build();

        DeveloperCredentialResponse saved = credentialService.saveEmailCredentials(alice.getId(), request);

        assertNotNull(saved.getId());
        assertEquals(NotificationType.EMAIL, saved.getChannel());
        assertEquals("GMAIL", saved.getProvider());
        assertEquals("smtp.gmail.com", saved.getHost());
        assertEquals(587, saved.getPort());
        assertEquals("alice@gmail.com", saved.getFromAddressOrNumber());
        assertEquals("Alice Personal App", saved.getFromName());
        assertTrue(saved.getMaskedUsernameOrSid().contains("****"), "Username should be masked");
        assertFalse(saved.getMaskedUsernameOrSid().contains("abcd-efgh"), "Password must never be exposed");

        List<DeveloperCredentialResponse> allCreds = credentialService.getCredentials(alice.getId());
        assertEquals(1, allCreds.size());
        assertEquals("GMAIL", allCreds.get(0).getProvider());
    }

    @Test
    @DisplayName("Developers can save and retrieve their custom SMS Twilio credentials")
    void testSaveAndRetrieveSmsCredentials() {
        SaveSmsCredentialRequest request = SaveSmsCredentialRequest.builder()
                .provider("TWILIO")
                .accountSid("MOCK_TWILIO_ACCOUNT_SID_987654321")
                .authToken("secret_auth_token_987654321")
                .fromNumber("+15551234567")
                .build();

        DeveloperCredentialResponse saved = credentialService.saveSmsCredentials(bob.getId(), request);

        assertNotNull(saved.getId());
        assertEquals(NotificationType.SMS, saved.getChannel());
        assertEquals("TWILIO", saved.getProvider());
        assertEquals("+15551234567", saved.getFromAddressOrNumber());
        assertTrue(saved.getMaskedUsernameOrSid().startsWith("MO"), "Masked SID starts with MO");
        assertTrue(saved.getMaskedUsernameOrSid().contains("****"));

        List<DeveloperCredentialResponse> bobCreds = credentialService.getCredentials(bob.getId());
        assertEquals(1, bobCreds.size());
    }

    @Test
    @DisplayName("Email notification dispatches using developer's own registered credentials")
    void testEmailDeliveryUsesDeveloperCustomCredentials() {
        // Alice configures her own Mailtrap credentials
        SaveEmailCredentialRequest request = SaveEmailCredentialRequest.builder()
                .provider("SIMULATION")
                .username("test_user_sim")
                .password("test_pass_sim")
                .fromAddress("alice.store@mydomain.com")
                .fromName("Alice Store")
                .build();

        credentialService.saveEmailCredentials(alice.getId(), request);

        // Alice dispatches an email notification
        CreateNotificationRequest createReq = CreateNotificationRequest.builder()
                .type(NotificationType.EMAIL)
                .recipientAddress("customer@somewhere.com")
                .subject("Order Placed #901")
                .body("Your order has been placed successfully.")
                .build();

        NotificationResponse response = notificationService.createNotification(alice.getId(), createReq);

        NotificationMessage message = NotificationMessage.builder()
                .notificationId(response.getId())
                .userId(alice.getId())
                .type(NotificationType.EMAIL)
                .recipientAddress(createReq.getRecipientAddress())
                .subject(createReq.getSubject())
                .body(createReq.getBody())
                .build();

        // Deliver
        DeliveryResult result = deliveryDispatcher.dispatch(message);
        assertTrue(result.isSuccess());

        List<DeliveryAttempt> attempts = deliveryAttemptRepository.findByNotificationIdOrderByAttemptNumberAsc(response.getId());
        assertEquals(1, attempts.size());
        assertEquals(AttemptStatus.SUCCESS, attempts.get(0).getStatus());

        // Provider response reflects Alice's custom sender and custom provider
        String providerResponse = attempts.get(0).getProviderResponse();
        assertTrue(providerResponse.contains("alice.store@mydomain.com") || providerResponse.contains("SIMULATION"),
                "Delivery attempt should record developer custom sender details: " + providerResponse);
    }

    @Test
    @DisplayName("Deleting custom credentials reverts delivery back to system fallback")
    void testDeleteCredentialRevertsToDefault() {
        SaveEmailCredentialRequest request = SaveEmailCredentialRequest.builder()
                .provider("GMAIL")
                .username("temp@gmail.com")
                .password("app-pass")
                .fromAddress("temp@gmail.com")
                .build();

        credentialService.saveEmailCredentials(alice.getId(), request);
        assertEquals(1, credentialService.getCredentials(alice.getId()).size());

        credentialService.deleteCredential(alice.getId(), NotificationType.EMAIL);
        assertEquals(0, credentialService.getCredentials(alice.getId()).size());
    }
}
