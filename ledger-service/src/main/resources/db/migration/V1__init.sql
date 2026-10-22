CREATE TABLE accounts (
    id              UUID PRIMARY KEY,
    owner_name      VARCHAR(255)   NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    balance         NUMERIC(19,4)  NOT NULL CHECK (balance >= 0),
    opening_balance NUMERIC(19,4)  NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE transfers (
    id              UUID PRIMARY KEY,
    idempotency_key VARCHAR(255)   NOT NULL,
    request_hash    VARCHAR(64)    NOT NULL,
    from_account_id UUID           NOT NULL,
    to_account_id   UUID           NOT NULL,
    amount          NUMERIC(19,4)  NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    reject_reason   VARCHAR(255),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_transfers_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_transfers_from_account ON transfers (from_account_id);
CREATE INDEX idx_transfers_to_account ON transfers (to_account_id);

CREATE TABLE ledger_entries (
    id          UUID PRIMARY KEY,
    transfer_id UUID           NOT NULL REFERENCES transfers (id),
    account_id  UUID           NOT NULL REFERENCES accounts (id),
    direction   VARCHAR(10)    NOT NULL,
    amount      NUMERIC(19,4)  NOT NULL CHECK (amount > 0),
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_ledger_entries_account ON ledger_entries (account_id, created_at DESC);
CREATE INDEX idx_ledger_entries_transfer ON ledger_entries (transfer_id);

CREATE TABLE outbox_events (
    id            UUID PRIMARY KEY,
    aggregate_id  UUID           NOT NULL,
    event_type    VARCHAR(40)    NOT NULL,
    payload       TEXT           NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
