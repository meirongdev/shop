ALTER TABLE buyer_profile
    ADD COLUMN invite_code VARCHAR(20) UNIQUE NULL,
    ADD COLUMN invite_code_expire_at TIMESTAMP(6) NULL,
    ADD COLUMN referrer_id VARCHAR(64) NULL,
    ADD COLUMN email_verified TINYINT(1) NOT NULL DEFAULT 0;

UPDATE buyer_profile
SET invite_code = CONCAT('INV-', UPPER(SUBSTRING(REPLACE(player_id, '-', ''), 1, 10))),
    invite_code_expire_at = DATE_ADD(created_at, INTERVAL 30 DAY)
WHERE invite_code IS NULL;

ALTER TABLE buyer_profile
    MODIFY COLUMN invite_code VARCHAR(20) NOT NULL,
    MODIFY COLUMN invite_code_expire_at TIMESTAMP(6) NOT NULL;

CREATE TABLE referral_record (
    id                   VARCHAR(64) PRIMARY KEY,
    invite_code          VARCHAR(20) NOT NULL,
    referrer_id          VARCHAR(64) NOT NULL,
    invitee_id           VARCHAR(64) NOT NULL,
    invitee_username     VARCHAR(128) NOT NULL,
    status               VARCHAR(20) NOT NULL,
    registered_at        TIMESTAMP(6) NULL,
    first_order_at       TIMESTAMP(6) NULL,
    reward_issued_at     TIMESTAMP(6) NULL,
    created_at           TIMESTAMP(6) NOT NULL,
    updated_at           TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_referral_invitee UNIQUE (invitee_id),
    INDEX idx_referral_referrer (referrer_id, created_at),
    INDEX idx_referral_status (status, reward_issued_at)
);
