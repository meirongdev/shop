ALTER TABLE user_account
    ADD COLUMN phone_number VARCHAR(32) NULL,
    ADD CONSTRAINT uk_user_account_phone UNIQUE (phone_number);
