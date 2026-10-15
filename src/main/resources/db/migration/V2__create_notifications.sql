-- =============================================================
-- V2: Create notifications table
-- =============================================================

CREATE TABLE notifications (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL
                          REFERENCES users (id) ON DELETE CASCADE,
    type              VARCHAR(20)  NOT NULL
                          CHECK (type IN ('EMAIL', 'SMS', 'IN_APP')),
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                          CHECK (status IN ('PENDING', 'PROCESSING', 'DELIVERED', 'FAILED', 'CANCELLED')),
    subject           VARCHAR(500),
    body              TEXT         NOT NULL,
    -- EMAIL: email address | SMS: E.164 phone | IN_APP: target user UUID
    recipient_address VARCHAR(255) NOT NULL,
    idempotency_key   VARCHAR(255),
    -- channel-specific extras (templateId, messagingServiceSid, variable map, etc.)
    channel_metadata  JSONB,
    scheduled_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_notifications_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_notifications_user_id
    ON notifications (user_id);

CREATE INDEX idx_notifications_status
    ON notifications (status);

CREATE INDEX idx_notifications_type
    ON notifications (type);

CREATE INDEX idx_notifications_created_at
    ON notifications (created_at DESC);

CREATE INDEX idx_notifications_scheduled_at
    ON notifications (scheduled_at)
    WHERE scheduled_at IS NOT NULL;

-- GIN index for fast JSONB queries on channel_metadata
CREATE INDEX idx_notifications_channel_metadata
    ON notifications USING GIN (channel_metadata)
    WHERE channel_metadata IS NOT NULL;

COMMENT ON TABLE  notifications                   IS 'Every notification request submitted to Dispatch';
COMMENT ON COLUMN notifications.recipient_address IS 'EMAIL: email address; SMS: E.164 phone; IN_APP: target user UUID';
COMMENT ON COLUMN notifications.idempotency_key   IS 'Caller-supplied key for duplicate detection (Part 07)';
COMMENT ON COLUMN notifications.channel_metadata  IS 'Channel-specific parameters stored as JSONB (templateId, Twilio sid, vars…)';
COMMENT ON COLUMN notifications.scheduled_at      IS 'NULL = process immediately; non-NULL = hold until this timestamp';
