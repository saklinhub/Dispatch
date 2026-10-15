-- =============================================================
-- V3: Create delivery_attempts table
-- =============================================================

CREATE TABLE delivery_attempts (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id   UUID        NOT NULL
                          REFERENCES notifications (id) ON DELETE CASCADE,
    attempt_number    INT         NOT NULL CHECK (attempt_number >= 1),
    status            VARCHAR(20) NOT NULL
                          CHECK (status IN ('SUCCESS', 'FAILURE', 'RETRYING')),
    -- Raw response from provider: Twilio Message SID, SMTP reply, etc.
    provider_response TEXT,
    -- Machine-readable error code for failure classification (Part 06)
    -- Examples: TWILIO_21211 (invalid number), SMTP_550 (mailbox unavailable)
    error_code        VARCHAR(100),
    error_message     TEXT,
    attempted_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    next_retry_at     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_delivery_attempt_per_notification
        UNIQUE (notification_id, attempt_number)
);

CREATE INDEX idx_delivery_attempts_notification_id
    ON delivery_attempts (notification_id);

CREATE INDEX idx_delivery_attempts_status
    ON delivery_attempts (status);

CREATE INDEX idx_delivery_attempts_next_retry_at
    ON delivery_attempts (next_retry_at)
    WHERE next_retry_at IS NOT NULL;

COMMENT ON TABLE  delivery_attempts                   IS 'Append-only audit trail of every delivery attempt per notification';
COMMENT ON COLUMN delivery_attempts.provider_response IS 'Raw response body from delivery provider (Twilio SID, SMTP reply)';
COMMENT ON COLUMN delivery_attempts.error_code        IS 'Machine-readable error code used for PERMANENT vs TRANSIENT classification in Part 06';
COMMENT ON COLUMN delivery_attempts.next_retry_at     IS 'Exponential-backoff retry timestamp set by retry logic in Part 06';
