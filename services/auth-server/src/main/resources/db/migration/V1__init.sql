CREATE TABLE user_account (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    principal_id VARCHAR(64)  NOT NULL UNIQUE,
    username     VARCHAR(128) NOT NULL UNIQUE,
    email        VARCHAR(255),
    display_name VARCHAR(128) NOT NULL,
    password_hash VARCHAR(255),
    portal       VARCHAR(32)  NOT NULL DEFAULT 'buyer',
    roles        VARCHAR(255) NOT NULL DEFAULT 'ROLE_BUYER',
    status       VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_email (email),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE social_account (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_account_id  BIGINT       NOT NULL,
    provider         VARCHAR(32)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email            VARCHAR(255),
    name             VARCHAR(128),
    avatar_url       VARCHAR(512),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_provider_uid (provider, provider_user_id),
    INDEX idx_user_account (user_account_id),
    CONSTRAINT fk_social_user FOREIGN KEY (user_account_id) REFERENCES user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed demo users (matching DemoUserDirectory)
INSERT INTO user_account (principal_id, username, email, display_name, password_hash, portal, roles) VALUES
    ('player-1001', 'buyer.demo', 'buyer.demo@example.com', 'Buyer Demo', '{bcrypt}$2a$10$dummyhashnotusedfordemousers000000000000000000000', 'buyer', 'ROLE_BUYER'),
    ('player-1002', 'buyer.vip', 'buyer.vip@example.com', 'Buyer VIP', '{bcrypt}$2a$10$dummyhashnotusedfordemousers000000000000000000000', 'buyer', 'ROLE_BUYER'),
    ('seller-2001', 'seller.demo', 'seller.demo@example.com', 'Seller Demo', '{bcrypt}$2a$10$dummyhashnotusedfordemousers000000000000000000000', 'seller', 'ROLE_SELLER');
