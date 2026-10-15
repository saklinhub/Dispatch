-- =============================================================
-- V1: Create users table
-- =============================================================

CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    -- E.164 format, e.g. +15555551234 — used as default SMS destination
    phone_number  VARCHAR(20),
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER'
                      CHECK (role IN ('USER', 'ADMIN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_users_email
    ON users (email);

CREATE INDEX idx_users_phone_number
    ON users (phone_number)
    WHERE phone_number IS NOT NULL;

COMMENT ON TABLE  users               IS 'Registered users of the Dispatch platform';
COMMENT ON COLUMN users.phone_number  IS 'E.164 phone number used as default SMS recipient; overridable in notification_preferences';
COMMENT ON COLUMN users.role          IS 'USER = standard account, ADMIN = platform operator';
