CREATE TABLE IF NOT EXISTS http_idempotency (
    operation VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    transaction_id VARCHAR(36) NOT NULL,
    payload TEXT NOT NULL,
    state VARCHAR(16) NOT NULL,
    response TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (operation, idempotency_key)
);
