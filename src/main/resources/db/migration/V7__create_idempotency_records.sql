-- =============================================================
-- V7: Create idempotency_records table
-- =============================================================

CREATE TABLE idempotency_records (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key      VARCHAR(255) NOT NULL,
    request_hash         VARCHAR(64)  NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS'
                             CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED')),
    response_status_code INT,
    response_body        TEXT,
    notification_id      UUID
                             REFERENCES notifications (id) ON DELETE SET NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    locked_until         TIMESTAMPTZ  NOT NULL DEFAULT (now() + INTERVAL '60 seconds'),

    CONSTRAINT uq_idempotency_records_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_idempotency_records_status
    ON idempotency_records (status);

CREATE INDEX idx_idempotency_records_created_at
    ON idempotency_records (created_at);

CREATE INDEX idx_idempotency_records_locked_until
    ON idempotency_records (locked_until);

COMMENT ON TABLE  idempotency_records                      IS 'Tracks in-flight and completed idempotent requests for deduplication';
COMMENT ON COLUMN idempotency_records.idempotency_key      IS 'Client-provided unique idempotency key';
COMMENT ON COLUMN idempotency_records.request_hash         IS 'SHA-256 fingerprint of the request payload';
COMMENT ON COLUMN idempotency_records.status               IS 'IN_PROGRESS, COMPLETED, or FAILED';
COMMENT ON COLUMN idempotency_records.response_status_code IS 'Cached HTTP status code for replay';
COMMENT ON COLUMN idempotency_records.response_body        IS 'Cached JSON response payload for replay';
COMMENT ON COLUMN idempotency_records.notification_id      IS 'Associated notification ID created by this idempotent request';
COMMENT ON COLUMN idempotency_records.locked_until         IS 'Lease expiration to release stale in-progress locks';
