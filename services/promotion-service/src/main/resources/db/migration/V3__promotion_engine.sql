-- V3: Promotion engine upgrade — add conditions/benefits JSON to promotion_offer

ALTER TABLE promotion_offer
    ADD COLUMN type VARCHAR(32) NOT NULL DEFAULT 'SIMPLE' AFTER description,
    ADD COLUMN conditions JSON AFTER type,
    ADD COLUMN benefits JSON AFTER conditions,
    ADD COLUMN stacking_policy VARCHAR(16) NOT NULL DEFAULT 'EXCLUSIVE' AFTER benefits,
    ADD COLUMN priority INT NOT NULL DEFAULT 0 AFTER stacking_policy,
    ADD COLUMN start_at TIMESTAMP(6) NULL AFTER priority,
    ADD COLUMN end_at TIMESTAMP(6) NULL AFTER start_at;
