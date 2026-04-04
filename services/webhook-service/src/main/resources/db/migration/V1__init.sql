CREATE TABLE webhook_endpoint (
    id         VARCHAR(64)  NOT NULL PRIMARY KEY,
    seller_id  VARCHAR(64)  NOT NULL,
    url        VARCHAR(1024) NOT NULL,
    secret     VARCHAR(256) NOT NULL COMMENT 'HMAC-SHA256 signing secret',
    event_types VARCHAR(1024) NOT NULL COMMENT 'Comma-separated event types',
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    description VARCHAR(256) DEFAULT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_webhook_seller (seller_id),
    INDEX idx_webhook_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE webhook_delivery (
    id              VARCHAR(64)  NOT NULL PRIMARY KEY,
    endpoint_id     VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    event_id        VARCHAR(64)  NOT NULL,
    payload         TEXT         NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, SUCCESS, FAILED, RETRYING',
    response_code   INT          DEFAULT NULL,
    response_body   TEXT         DEFAULT NULL,
    attempt_count   INT          NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMP    NULL DEFAULT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_delivery_endpoint (endpoint_id),
    INDEX idx_delivery_status (status),
    INDEX idx_delivery_retry (status, next_retry_at),
    UNIQUE KEY uk_delivery_event (endpoint_id, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
