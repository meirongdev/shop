CREATE TABLE IF NOT EXISTS promotion_idempotency_key (
    idempotency_key VARCHAR(128) NOT NULL PRIMARY KEY,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) COMMENT 'Idempotency key store for promotion Kafka consumers';
