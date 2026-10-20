-- =============================================================
-- V6: Create outbox_events table  (Transactional Outbox Pattern)
-- =============================================================

CREATE TABLE outbox_events (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Domain entity type, e.g. NOTIFICATION, USER
    aggregate_type VARCHAR(100) NOT NULL,
    -- ID of the domain entity this event describes
    aggregate_id   UUID         NOT NULL,
    -- Event name, e.g. NOTIFICATION_CREATED, NOTIFICATION_STATUS_CHANGED
    event_type     VARCHAR(100) NOT NULL,
    -- Full event payload as JSONB — consumed by the outbox poller
    payload        JSONB        NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    error_message  TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

-- Partial index: only PENDING rows need fast lookup by the poller
CREATE INDEX idx_outbox_events_status
    ON outbox_events (status, created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_outbox_events_aggregate_id
    ON outbox_events (aggregate_id);

CREATE INDEX idx_outbox_events_created_at
    ON outbox_events (created_at);

-- GIN index for payload queries
CREATE INDEX idx_outbox_events_payload
    ON outbox_events USING GIN (payload);

COMMENT ON TABLE  outbox_events              IS 'Transactional outbox — events written atomically with domain changes, published to RabbitMQ by poller';
COMMENT ON COLUMN outbox_events.aggregate_type IS 'Domain entity type (NOTIFICATION, USER, etc.)';
COMMENT ON COLUMN outbox_events.event_type     IS 'Event name (NOTIFICATION_CREATED, STATUS_CHANGED, etc.)';
COMMENT ON COLUMN outbox_events.payload        IS 'Full serialised event payload as JSONB';
COMMENT ON COLUMN outbox_events.status         IS 'PENDING = awaiting publish, PUBLISHED = broker confirmed, FAILED = retries exhausted';
