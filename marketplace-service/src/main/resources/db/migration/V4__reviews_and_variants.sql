-- V4: product reviews + multi-SKU variants

CREATE TABLE IF NOT EXISTS product_review (
    id          VARCHAR(36)   NOT NULL PRIMARY KEY,
    product_id  VARCHAR(36)   NOT NULL,
    buyer_id    VARCHAR(64)   NOT NULL,
    order_id    VARCHAR(36),
    rating      TINYINT       NOT NULL,
    content     VARCHAR(2000),
    images      JSON,
    status      VARCHAR(20)   NOT NULL DEFAULT 'APPROVED',
    created_at  TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_product (product_id),
    INDEX idx_buyer (buyer_id),
    INDEX idx_product_status (product_id, status)
);

CREATE TABLE IF NOT EXISTS product_variant (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    product_id    VARCHAR(36)   NOT NULL,
    variant_name  VARCHAR(128)  NOT NULL,
    attributes    JSON          NOT NULL,
    price_adjust  DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    inventory     INT           NOT NULL DEFAULT 0,
    sku_suffix    VARCHAR(32),
    display_order INT           NOT NULL DEFAULT 0,
    created_at    TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (product_id) REFERENCES marketplace_product(id),
    INDEX idx_product (product_id)
);

-- Add review summary columns to marketplace_product
ALTER TABLE marketplace_product
    ADD COLUMN review_count INT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN avg_rating DECIMAL(3,2) NOT NULL DEFAULT 0.00 AFTER review_count;
