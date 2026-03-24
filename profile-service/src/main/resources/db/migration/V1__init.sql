CREATE TABLE IF NOT EXISTS buyer_profile (
    player_id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(128) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    email VARCHAR(256) NOT NULL,
    tier VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

INSERT INTO buyer_profile (player_id, username, display_name, email, tier, created_at, updated_at)
VALUES
  ('player-1001', 'buyer.demo', 'Buyer Demo', 'buyer.demo@example.com', 'SILVER', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
  ('player-1002', 'buyer.vip', 'Buyer VIP', 'buyer.vip@example.com', 'GOLD', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(6);
