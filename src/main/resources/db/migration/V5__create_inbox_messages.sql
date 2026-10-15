-- =============================================================
-- V5: Create inbox_messages table  (IN_APP channel)
-- =============================================================

CREATE TABLE inbox_messages (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL
                        REFERENCES users (id) ON DELETE CASCADE,
    notification_id UUID        NOT NULL
                        REFERENCES notifications (id) ON DELETE CASCADE,
    title           VARCHAR(500),
    body            TEXT        NOT NULL,
    is_read         BOOLEAN     NOT NULL DEFAULT FALSE,
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Fast unread-count query per user
CREATE INDEX idx_inbox_messages_user_id
    ON inbox_messages (user_id);

CREATE INDEX idx_inbox_messages_is_read
    ON inbox_messages (user_id, is_read);

-- Ordered inbox fetch (newest first)
CREATE INDEX idx_inbox_messages_created_at
    ON inbox_messages (user_id, created_at DESC);

COMMENT ON TABLE  inbox_messages         IS 'In-app notifications persisted to user inboxes (IN_APP channel)';
COMMENT ON COLUMN inbox_messages.is_read IS 'Whether the user has acknowledged this message';
COMMENT ON COLUMN inbox_messages.read_at IS 'Timestamp when the user first marked the message as read';
