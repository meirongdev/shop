CREATE TABLE IF NOT EXISTS cart_item (
    id VARCHAR(36) PRIMARY KEY,
    buyer_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    product_price DECIMAL(19,2) NOT NULL,
    seller_id VARCHAR(64) NOT NULL,
    quantity INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_buyer_product (buyer_id, product_id)
);

CREATE TABLE IF NOT EXISTS shop_order (
    id VARCHAR(36) PRIMARY KEY,
    buyer_id VARCHAR(64) NOT NULL,
    seller_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    subtotal DECIMAL(19,2) NOT NULL,
    discount_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(19,2) NOT NULL,
    coupon_id VARCHAR(36) NULL,
    coupon_code VARCHAR(64) NULL,
    payment_transaction_id VARCHAR(36) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    INDEX idx_buyer_id (buyer_id),
    INDEX idx_seller_id (seller_id)
);

CREATE TABLE IF NOT EXISTS order_item (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    product_price DECIMAL(19,2) NOT NULL,
    quantity INT NOT NULL,
    line_total DECIMAL(19,2) NOT NULL,
    INDEX idx_order_id (order_id)
);
