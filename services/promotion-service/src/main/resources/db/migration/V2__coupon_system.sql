CREATE TABLE IF NOT EXISTS coupon (
    id VARCHAR(36) PRIMARY KEY,
    seller_id VARCHAR(64) NOT NULL,
    code VARCHAR(64) NOT NULL UNIQUE,
    discount_type VARCHAR(32) NOT NULL,
    discount_value DECIMAL(19,2) NOT NULL,
    min_order_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    max_discount DECIMAL(19,2) NULL,
    usage_limit INT NOT NULL DEFAULT 0,
    used_count INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP(6) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS coupon_usage (
    id VARCHAR(36) PRIMARY KEY,
    coupon_id VARCHAR(36) NOT NULL,
    buyer_id VARCHAR(64) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    discount_applied DECIMAL(19,2) NOT NULL,
    used_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_coupon_order (coupon_id, order_id)
);

INSERT INTO coupon (id, seller_id, code, discount_type, discount_value, min_order_amount, max_discount, usage_limit, used_count, expires_at, active, created_at)
VALUES
  ('d0000000-0000-0000-0000-000000000001', 'seller-2001', 'SAVE10', 'FIXED_AMOUNT', 10.00, 50.00, NULL, 100, 0, NULL, TRUE, CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE discount_value = VALUES(discount_value);
