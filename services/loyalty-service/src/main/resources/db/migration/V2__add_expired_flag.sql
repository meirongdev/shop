-- V2: Add expired flag to loyalty_transaction for batch expiry processing

ALTER TABLE loyalty_transaction ADD COLUMN expired TINYINT(1) NOT NULL DEFAULT 0;
CREATE INDEX idx_expiry ON loyalty_transaction (type, expired, expire_at);
