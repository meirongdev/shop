CREATE TABLE IF NOT EXISTS wallet_account (
    player_id VARCHAR(64) PRIMARY KEY,
    balance DECIMAL(19,2) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS wallet_transaction (
    id VARCHAR(36) PRIMARY KEY,
    player_id VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS wallet_outbox_event (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_id VARCHAR(64) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    published BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6) NULL
);

INSERT INTO wallet_account (player_id, balance, updated_at)
VALUES
  ('player-1001', 250.00, CURRENT_TIMESTAMP(6)),
  ('player-1002', 500.00, CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE balance = VALUES(balance), updated_at = CURRENT_TIMESTAMP(6);
