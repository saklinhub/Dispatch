package com.dispatch.preference;

import com.dispatch.entity.NotificationPreference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.*;

/**
 * Timezone-aware evaluator for user quiet hours and do-not-disturb delivery windows.
 */
@Slf4j
@Component
public class QuietHoursEvaluator {

    /**
     * Checks if the given instant falls within the user's configured quiet hours window.
     */
    public boolean isWithinQuietHours(NotificationPreference preference, Instant instant) {
        if (preference == null) {
            return false;
        }

        LocalTime start = preference.getQuietHoursStart();
        LocalTime end = preference.getQuietHoursEnd();

        if (start == null || end == null || start.equals(end)) {
            return false;
        }

        ZoneId zoneId = resolveZoneId(preference.getTimezone());
        LocalTime localTime = instant.atZone(zoneId).toLocalTime();

        if (start.isBefore(end)) {
            // Intraday window, e.g. 13:00 to 17:00
            return !localTime.isBefore(start) && localTime.isBefore(end);
        } else {
            // Overnight window across midnight, e.g. 22:00 to 08:00
            return !localTime.isBefore(start) || localTime.isBefore(end);
        }
    }

    /**
     * Calculates the instant when the current quiet hours window ends in the user's timezone.
     * If not currently in quiet hours, returns the base instant.
     */
    public Instant calculateQuietHoursEnd(NotificationPreference preference, Instant from) {
        if (!isWithinQuietHours(preference, from)) {
            return from;
        }

        LocalTime start = preference.getQuietHoursStart();
        LocalTime end = preference.getQuietHoursEnd();
        ZoneId zoneId = resolveZoneId(preference.getTimezone());
        ZonedDateTime zdt = from.atZone(zoneId);
        LocalTime localTime = zdt.toLocalTime();

        LocalDate targetDate;
        if (start.isBefore(end)) {
            // Intraday window: ends today at 'end'
            targetDate = zdt.toLocalDate();
        } else {
            // Overnight window:
            // If before midnight (e.g. 23:30), ends tomorrow morning at 'end'
            // If after midnight (e.g. 03:00), ends today morning at 'end'
            if (!localTime.isBefore(start)) {
                targetDate = zdt.toLocalDate().plusDays(1);
            } else {
                targetDate = zdt.toLocalDate();
            }
        }

        ZonedDateTime endZdt = ZonedDateTime.of(targetDate, end, zoneId);
        return endZdt.toInstant();
    }

    /**
     * Resolves an IANA timezone string into a valid ZoneId, falling back to UTC.
     */
    public ZoneId resolveZoneId(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (Exception ex) {
            log.warn("Invalid timezone string '{}', falling back to UTC", timezone);
            return ZoneOffset.UTC;
        }
    }

    /**
     * Validates whether a timezone identifier string is a recognized IANA timezone.
     */
    public boolean isValidTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return false;
        }
        try {
            ZoneId.of(timezone.trim());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
