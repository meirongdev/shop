CREATE TABLE subscription_plan (
    id          VARCHAR(64)   NOT NULL PRIMARY KEY,
    seller_id   VARCHAR(64)   NOT NULL,
    product_id  VARCHAR(64)   NOT NULL,
    name        VARCHAR(256)  NOT NULL,
    description TEXT          DEFAULT NULL,
    price       DECIMAL(12,2) NOT NULL,
    frequency   VARCHAR(32)   NOT NULL COMMENT 'WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY',
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_plan_seller (seller_id),
    INDEX idx_plan_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE subscription (
    id              VARCHAR(64)   NOT NULL PRIMARY KEY,
    buyer_id        VARCHAR(64)   NOT NULL,
    plan_id         VARCHAR(64)   NOT NULL,
    status          VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, PAUSED, CANCELLED, EXPIRED',
    quantity        INT           NOT NULL DEFAULT 1,
    next_renewal_at TIMESTAMP     NULL DEFAULT NULL,
    last_order_id   VARCHAR(64)   DEFAULT NULL,
    total_renewals  INT           NOT NULL DEFAULT 0,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sub_buyer (buyer_id),
    INDEX idx_sub_plan (plan_id),
    INDEX idx_sub_renewal (status, next_renewal_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE subscription_order_log (
    id              VARCHAR(64) NOT NULL PRIMARY KEY,
    subscription_id VARCHAR(64) NOT NULL,
    order_id        VARCHAR(64) NOT NULL,
    renewal_number  INT         NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED, PAID, FAILED',
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_log_sub (subscription_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
