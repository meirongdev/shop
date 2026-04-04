-- V3: Guest shopping support

ALTER TABLE shop_order
    ADD COLUMN order_token VARCHAR(64) NULL AFTER type,
    ADD COLUMN guest_email VARCHAR(256) NULL AFTER order_token,
    ADD UNIQUE INDEX idx_order_token (order_token);
