-- V4: Coupon template/instance separation
-- coupon_template holds the immutable discount rule definition
-- coupon_instance holds per-buyer coupon issuances from a template

CREATE TABLE IF NOT EXISTS coupon_template (
    id               VARCHAR(36)    PRIMARY KEY,
    seller_id        VARCHAR(64)    NOT NULL,
    code             VARCHAR(64)    NOT NULL UNIQUE,
    title            VARCHAR(128)   NOT NULL DEFAULT '',
    discount_type    VARCHAR(32)    NOT NULL,
    discount_value   DECIMAL(19,2)  NOT NULL,
    min_order_amount DECIMAL(19,2)  NOT NULL DEFAULT 0.00,
    max_discount     DECIMAL(19,2)  NULL,
    total_limit      INT            NOT NULL DEFAULT 0 COMMENT '0 = unlimited',
    per_user_limit   INT            NOT NULL DEFAULT 1,
    valid_days       INT            NOT NULL DEFAULT 14 COMMENT 'Days after issuance the instance is valid',
    active           BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) COMMENT 'Immutable discount rule definition';

CREATE TABLE IF NOT EXISTS coupon_instance (
    id               VARCHAR(36)    PRIMARY KEY,
    template_id      VARCHAR(36)    NOT NULL,
    buyer_id         VARCHAR(64)    NOT NULL,
    code             VARCHAR(64)    NOT NULL,
    status           VARCHAR(16)    NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE, USED, EXPIRED',
    order_id         VARCHAR(36)    NULL,
    discount_applied DECIMAL(19,2)  NULL,
    expires_at       TIMESTAMP(6)   NOT NULL,
    used_at          TIMESTAMP(6)   NULL,
    created_at       TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_buyer_status (buyer_id, status),
    INDEX idx_template (template_id),
    UNIQUE KEY uk_template_buyer_instance (template_id, buyer_id, code)
) COMMENT 'Per-buyer coupon issued from a template';

-- Seed a global template for the existing SAVE10 coupon
INSERT INTO coupon_template (id, seller_id, code, title, discount_type, discount_value, min_order_amount, max_discount, total_limit, per_user_limit, valid_days, active)
VALUES ('t0000000-0000-0000-0000-000000000001', 'seller-2001', 'SAVE10', '$10 Off', 'FIXED_AMOUNT', 10.00, 50.00, NULL, 100, 1, 365, TRUE)
ON DUPLICATE KEY UPDATE discount_value = VALUES(discount_value);
