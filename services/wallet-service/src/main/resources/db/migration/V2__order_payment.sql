ALTER TABLE wallet_transaction
    ADD COLUMN reference_id VARCHAR(64) NULL AFTER provider_reference,
    ADD COLUMN reference_type VARCHAR(32) NULL AFTER reference_id;
