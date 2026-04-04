CREATE TABLE IF NOT EXISTS marketplace_product (
    id VARCHAR(36) PRIMARY KEY,
    seller_id VARCHAR(64) NOT NULL,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NOT NULL,
    price DECIMAL(19,2) NOT NULL,
    inventory INT NOT NULL,
    published BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

INSERT INTO marketplace_product (id, seller_id, sku, name, description, price, inventory, published, created_at)
VALUES
  ('10000000-0000-0000-0000-000000000001', 'seller-2001', 'SKU-HAIR-DRYER', 'Ionic Hair Dryer', 'Cloud-native demo product for buyer marketplace.', 89.90, 30, TRUE, CURRENT_TIMESTAMP(6)),
  ('10000000-0000-0000-0000-000000000002', 'seller-2001', 'SKU-BEAUTY-KIT', 'Beauty Starter Kit', 'Starter bundle for the seller portal showcase.', 129.00, 12, TRUE, CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE inventory = VALUES(inventory);
