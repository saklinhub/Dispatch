package com.dispatch.service;

import com.dispatch.dto.request.UpdatePreferenceRequest;
import com.dispatch.dto.response.PreferenceResponse;
import com.dispatch.entity.NotificationPreference;
import com.dispatch.entity.User;
import com.dispatch.entity.enums.NotificationType;
import com.dispatch.mapper.PreferenceMapper;
import com.dispatch.preference.QuietHoursEvaluator;
import com.dispatch.repository.NotificationPreferenceRepository;
import com.dispatch.repository.UserRepository;
import com.dispatch.service.impl.PreferenceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PreferenceServiceTest {

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @Mock
    private UserRepository userRepository;

    private QuietHoursEvaluator quietHoursEvaluator;
    private PreferenceMapper mapper;
    private PreferenceServiceImpl preferenceService;

    private UUID userId;
    private User testUser;
    private NotificationPreference testPref;

    @BeforeEach
    void setUp() {
        quietHoursEvaluator = new QuietHoursEvaluator();
        mapper = new PreferenceMapper();
        preferenceService = new PreferenceServiceImpl(preferenceRepository, userRepository, quietHoursEvaluator, mapper);

        userId = UUID.randomUUID();
        testUser = User.builder().id(userId).email("user@dispatch.io").build();
        testPref = NotificationPreference.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .emailEnabled(true)
                .smsEnabled(true)
                .inAppEnabled(true)
                .timezone("UTC")
                .build();
    }

    @Test
    @DisplayName("getPreferences returns existing preference")
    void testGetPreferencesExisting() {
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(testPref));

        PreferenceResponse response = preferenceService.getPreferences(userId);
        assertNotNull(response);
        assertEquals(userId, response.getUserId());
        assertTrue(response.isEmailEnabled());
        assertTrue(response.isSmsEnabled());
        assertEquals("UTC", response.getTimezone());
    }

    @Test
    @DisplayName("getOrCreateDefault creates default preference when none exists")
    void testGetPreferencesCreatesDefault() {
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(preferenceRepository.save(any(NotificationPreference.class))).thenAnswer(i -> {
            NotificationPreference p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PreferenceResponse response = preferenceService.getPreferences(userId);
        assertNotNull(response);
        assertTrue(response.isEmailEnabled());
        assertTrue(response.isSmsEnabled());
        assertTrue(response.isInAppEnabled());
        assertEquals("UTC", response.getTimezone());
    }

    @Test
    @DisplayName("updatePreferences updates fields and validates timezone")
    void testUpdatePreferencesSuccess() {
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(testPref));
        when(preferenceRepository.save(any(NotificationPreference.class))).thenAnswer(i -> i.getArgument(0));

        UpdatePreferenceRequest req = UpdatePreferenceRequest.builder()
                .smsEnabled(false)
                .preferredEmail("work@company.com")
                .preferredPhone("+15555550999")
                .quietHoursStart(LocalTime.of(22, 0))
                .quietHoursEnd(LocalTime.of(8, 0))
                .timezone("America/New_York")
                .build();

        PreferenceResponse response = preferenceService.updatePreferences(userId, req);
        assertFalse(response.isSmsEnabled());
        assertTrue(response.isEmailEnabled());
        assertEquals("work@company.com", response.getPreferredEmail());
        assertEquals("+15555550999", response.getPreferredPhone());
        assertEquals("America/New_York", response.getTimezone());
        assertEquals(LocalTime.of(22, 0), response.getQuietHoursStart());
        assertEquals(LocalTime.of(8, 0), response.getQuietHoursEnd());
    }

    @Test
    @DisplayName("updatePreferences rejects invalid IANA timezone with IllegalArgumentException")
    void testUpdatePreferencesRejectsInvalidTimezone() {
        UpdatePreferenceRequest req = UpdatePreferenceRequest.builder()
                .timezone("Invalid/Bad_Timezone")
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                preferenceService.updatePreferences(userId, req)
        );
    }

    @Test
    @DisplayName("toggleChannel modifies only the specified channel")
    void testToggleChannel() {
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(testPref));
        when(preferenceRepository.save(any(NotificationPreference.class))).thenAnswer(i -> i.getArgument(0));

        PreferenceResponse response = preferenceService.toggleChannel(userId, NotificationType.SMS, false);
        assertFalse(response.isSmsEnabled());
        assertTrue(response.isEmailEnabled());
        assertTrue(response.isInAppEnabled());
    }
}
