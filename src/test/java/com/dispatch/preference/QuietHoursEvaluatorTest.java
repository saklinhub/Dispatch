package com.dispatch.preference;

import com.dispatch.entity.NotificationPreference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

class QuietHoursEvaluatorTest {

    private QuietHoursEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new QuietHoursEvaluator();
    }

    @Test
    @DisplayName("Intraday quiet hours window correctly detects times within and outside window")
    void testIntradayQuietHours() {
        NotificationPreference pref = NotificationPreference.builder()
                .quietHoursStart(LocalTime.of(13, 0)) // 1:00 PM
                .quietHoursEnd(LocalTime.of(17, 0))   // 5:00 PM
                .timezone("UTC")
                .build();

        // 12:30 UTC - outside
        Instant before = Instant.parse("2026-10-22T12:30:00Z");
        assertFalse(evaluator.isWithinQuietHours(pref, before));

        // 14:15 UTC - inside
        Instant inside = Instant.parse("2026-10-22T14:15:00Z");
        assertTrue(evaluator.isWithinQuietHours(pref, inside));

        // 17:30 UTC - outside
        Instant after = Instant.parse("2026-10-22T17:30:00Z");
        assertFalse(evaluator.isWithinQuietHours(pref, after));

        // Calculate end
        Instant end = evaluator.calculateQuietHoursEnd(pref, inside);
        assertEquals(Instant.parse("2026-10-22T17:00:00Z"), end);
    }

    @Test
    @DisplayName("Overnight quiet hours window across midnight correctly detects late night and early morning")
    void testOvernightQuietHours() {
        NotificationPreference pref = NotificationPreference.builder()
                .quietHoursStart(LocalTime.of(22, 0)) // 10:00 PM
                .quietHoursEnd(LocalTime.of(8, 0))    // 8:00 AM
                .timezone("UTC")
                .build();

        // 23:15 UTC (before midnight) - inside
        Instant lateNight = Instant.parse("2026-10-22T23:15:00Z");
        assertTrue(evaluator.isWithinQuietHours(pref, lateNight));

        // calculateQuietHoursEnd from 23:15 on Oct 22 should return 08:00 on Oct 23
        Instant endFromLateNight = evaluator.calculateQuietHoursEnd(pref, lateNight);
        assertEquals(Instant.parse("2026-10-23T08:00:00Z"), endFromLateNight);

        // 03:45 UTC (after midnight) - inside
        Instant earlyMorning = Instant.parse("2026-10-23T03:45:00Z");
        assertTrue(evaluator.isWithinQuietHours(pref, earlyMorning));

        // calculateQuietHoursEnd from 03:45 on Oct 23 should return 08:00 on Oct 23
        Instant endFromEarlyMorning = evaluator.calculateQuietHoursEnd(pref, earlyMorning);
        assertEquals(Instant.parse("2026-10-23T08:00:00Z"), endFromEarlyMorning);

        // 12:00 UTC (daytime) - outside
        Instant daytime = Instant.parse("2026-10-23T12:00:00Z");
        assertFalse(evaluator.isWithinQuietHours(pref, daytime));
    }

    @Test
    @DisplayName("Timezone conversion applies quiet hours in user's local timezone")
    void testTimezoneAwareEvaluation() {
        // User in New York (EDT, UTC-4 in October)
        NotificationPreference pref = NotificationPreference.builder()
                .quietHoursStart(LocalTime.of(22, 0)) // 10:00 PM EDT
                .quietHoursEnd(LocalTime.of(7, 0))    // 7:00 AM EDT
                .timezone("America/New_York")
                .build();

        // 03:00 UTC on Oct 23 corresponds to 23:00 EDT on Oct 22 (inside quiet hours)
        Instant instantInside = Instant.parse("2026-10-23T03:00:00Z");
        assertTrue(evaluator.isWithinQuietHours(pref, instantInside));

        // 15:00 UTC on Oct 23 corresponds to 11:00 EDT on Oct 23 (outside quiet hours)
        Instant instantOutside = Instant.parse("2026-10-23T15:00:00Z");
        assertFalse(evaluator.isWithinQuietHours(pref, instantOutside));

        // calculateQuietHoursEnd from 03:00 UTC (23:00 EDT) should be 07:00 EDT = 11:00 UTC
        Instant endInstant = evaluator.calculateQuietHoursEnd(pref, instantInside);
        ZonedDateTime endZdt = endInstant.atZone(ZoneId.of("America/New_York"));
        assertEquals(LocalTime.of(7, 0), endZdt.toLocalTime());
    }

    @Test
    @DisplayName("Null or empty quiet hours returns false")
    void testNullQuietHours() {
        NotificationPreference nullPref = null;
        assertFalse(evaluator.isWithinQuietHours(nullPref, Instant.now()));

        NotificationPreference unconfiguredPref = NotificationPreference.builder().build();
        assertFalse(evaluator.isWithinQuietHours(unconfiguredPref, Instant.now()));
    }

    @Test
    @DisplayName("Timezone validator recognizes valid and invalid IANA timezone identifiers")
    void testTimezoneValidation() {
        assertTrue(evaluator.isValidTimezone("UTC"));
        assertTrue(evaluator.isValidTimezone("America/New_York"));
        assertTrue(evaluator.isValidTimezone("Asia/Kolkata"));
        assertTrue(evaluator.isValidTimezone("Europe/London"));

        assertFalse(evaluator.isValidTimezone("Invalid/Timezone"));
        assertFalse(evaluator.isValidTimezone(""));
        assertFalse(evaluator.isValidTimezone(null));
    }
}
