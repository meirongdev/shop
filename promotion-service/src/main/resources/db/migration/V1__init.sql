CREATE TABLE IF NOT EXISTS promotion_offer (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    title VARCHAR(128) NOT NULL,
    description VARCHAR(512) NOT NULL,
    reward_amount DECIMAL(19,2) NOT NULL,
    active BOOLEAN NOT NULL,
    source VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

INSERT INTO promotion_offer (id, code, title, description, reward_amount, active, source, created_at)
VALUES
  ('20000000-0000-0000-0000-000000000001', 'WELCOME-10', 'Welcome Promotion', 'Base promotion shipped with the technical validation project.', 10.00, TRUE, 'seller-2001', CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE reward_amount = VALUES(reward_amount);
