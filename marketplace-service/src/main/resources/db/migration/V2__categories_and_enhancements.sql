CREATE TABLE IF NOT EXISTS product_category (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE,
    description VARCHAR(256) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

INSERT INTO product_category (id, name, description, created_at) VALUES
  ('c0000000-0000-0000-0000-000000000001', 'Hair Care', 'Hair dryers, styling tools, and hair treatments', CURRENT_TIMESTAMP(6)),
  ('c0000000-0000-0000-0000-000000000002', 'Skin Care', 'Moisturizers, serums, and skin treatments', CURRENT_TIMESTAMP(6)),
  ('c0000000-0000-0000-0000-000000000003', 'Beauty Kits', 'Curated beauty bundles and starter kits', CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE description = VALUES(description);

ALTER TABLE marketplace_product
    ADD COLUMN category_id VARCHAR(36) NULL AFTER published,
    ADD COLUMN image_url VARCHAR(512) NULL AFTER category_id,
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED' AFTER image_url,
    ADD COLUMN updated_at TIMESTAMP(6) NULL AFTER created_at;

UPDATE marketplace_product SET category_id = 'c0000000-0000-0000-0000-000000000001', status = 'PUBLISHED', updated_at = CURRENT_TIMESTAMP(6)
WHERE sku = 'SKU-HAIR-DRYER';
UPDATE marketplace_product SET category_id = 'c0000000-0000-0000-0000-000000000003', status = 'PUBLISHED', updated_at = CURRENT_TIMESTAMP(6)
WHERE sku = 'SKU-BEAUTY-KIT';
