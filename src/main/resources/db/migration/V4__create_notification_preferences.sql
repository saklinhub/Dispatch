-- =============================================================
-- V4: Create notification_preferences table
-- =============================================================

CREATE TABLE notification_preferences (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL UNIQUE
                          REFERENCES users (id) ON DELETE CASCADE,
    -- Channel opt-ins
    email_enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    sms_enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    in_app_enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Override defaults from the users table
    preferred_email   VARCHAR(255),           -- overrides users.email for notifications
    preferred_phone   VARCHAR(20),            -- overrides users.phone_number for SMS (E.164)
    -- Quiet hours (do-not-disturb window)
    quiet_hours_start TIME,
    quiet_hours_end   TIME,
    -- IANA timezone for quiet-hour evaluation, e.g. 'America/New_York', 'Asia/Kolkata'
    timezone          VARCHAR(100) NOT NULL DEFAULT 'UTC',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_notification_preferences_user_id
    ON notification_preferences (user_id);

COMMENT ON TABLE  notification_preferences                   IS 'Per-user channel opt-ins and delivery preferences';
COMMENT ON COLUMN notification_preferences.preferred_phone   IS 'E.164 phone override for SMS; null falls back to users.phone_number';
COMMENT ON COLUMN notification_preferences.quiet_hours_start IS 'Start of do-not-disturb window in local time';
COMMENT ON COLUMN notification_preferences.quiet_hours_end   IS 'End of do-not-disturb window in local time';
COMMENT ON COLUMN notification_preferences.timezone          IS 'IANA timezone used to evaluate quiet hours (e.g. America/New_York)';
