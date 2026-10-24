-- =============================================================
-- V8: Create developer_channel_credentials table for multi-tenant sender credentials
-- =============================================================

CREATE TABLE developer_channel_credentials (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    channel           VARCHAR(20)  NOT NULL CHECK (channel IN ('EMAIL', 'SMS')),
    provider          VARCHAR(50)  NOT NULL,
    credentials_json  JSONB        NOT NULL,
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_developer_channel_credentials_user_channel UNIQUE (user_id, channel)
);

CREATE INDEX idx_dev_credentials_user_id ON developer_channel_credentials (user_id);
CREATE INDEX idx_dev_credentials_user_channel ON developer_channel_credentials (user_id, channel);

COMMENT ON TABLE developer_channel_credentials IS 'Per-developer custom delivery credentials (e.g. personal Gmail, SendGrid, Mailtrap, Twilio)';
COMMENT ON COLUMN developer_channel_credentials.credentials_json IS 'JSONB payload containing host, port, username, password/apiKey, fromAddress, fromName, etc.';
