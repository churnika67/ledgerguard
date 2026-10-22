CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    id          UUID PRIMARY KEY,
    account_id  UUID         NOT NULL,
    transfer_id UUID         NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    message     VARCHAR(500) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_account ON notifications (account_id, created_at DESC);
