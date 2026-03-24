-- V3: Seller storefront (multi-seller marketplace)

ALTER TABLE seller_profile
    ADD COLUMN shop_name VARCHAR(128) NULL AFTER display_name,
    ADD COLUMN shop_slug VARCHAR(64) NULL AFTER shop_name,
    ADD COLUMN shop_description TEXT NULL AFTER shop_slug,
    ADD COLUMN logo_url VARCHAR(512) NULL AFTER shop_description,
    ADD COLUMN banner_url VARCHAR(512) NULL AFTER logo_url,
    ADD COLUMN avg_rating DECIMAL(3,2) NOT NULL DEFAULT 0.00 AFTER banner_url,
    ADD COLUMN total_sales INT NOT NULL DEFAULT 0 AFTER avg_rating,
    ADD UNIQUE INDEX idx_shop_slug (shop_slug);

UPDATE seller_profile SET shop_name = 'Demo Store', shop_slug = 'demo-store',
    shop_description = 'Your one-stop shop for quality products' WHERE seller_id = 'seller-2001';
