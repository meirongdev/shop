CREATE TABLE IF NOT EXISTS wallet_idempotency_key (
    idempotency_key VARCHAR(128) NOT NULL PRIMARY KEY,
    transaction_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) COMMENT 'Idempotency key store for wallet write operations';
