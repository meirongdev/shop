-- V2: Order state machine enhancement
-- Adds new columns to shop_order, creates order_shipment, order_refund, order_outbox_event tables

-- 1. Add new columns to shop_order
ALTER TABLE shop_order
    ADD COLUMN order_no       VARCHAR(32)    NULL AFTER id,
    ADD COLUMN type           VARCHAR(32)    NOT NULL DEFAULT 'STANDARD' AFTER order_no,
    ADD COLUMN cancel_reason  VARCHAR(256)   NULL AFTER coupon_code,
    ADD COLUMN paid_at        TIMESTAMP(6)   NULL,
    ADD COLUMN shipped_at     TIMESTAMP(6)   NULL,
    ADD COLUMN delivered_at   TIMESTAMP(6)   NULL,
    ADD COLUMN completed_at   TIMESTAMP(6)   NULL,
    ADD COLUMN cancelled_at   TIMESTAMP(6)   NULL;

-- Backfill order_no for existing rows
UPDATE shop_order SET order_no = CONCAT('ORD-', UPPER(SUBSTRING(id, 1, 12))) WHERE order_no IS NULL;
ALTER TABLE shop_order MODIFY COLUMN order_no VARCHAR(32) NOT NULL;
ALTER TABLE shop_order ADD UNIQUE INDEX uk_order_no (order_no);

-- Additional indexes
ALTER TABLE shop_order ADD INDEX idx_status_created (status, created_at);

-- 2. Shipment table
CREATE TABLE IF NOT EXISTS order_shipment (
    id                VARCHAR(36)   NOT NULL PRIMARY KEY,
    order_id          VARCHAR(36)   NOT NULL UNIQUE,
    carrier           VARCHAR(64),
    tracking_no       VARCHAR(128),
    tracking_url      VARCHAR(512),
    status            VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    created_at        TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_order_id (order_id)
);

-- 3. Refund table
CREATE TABLE IF NOT EXISTS order_refund (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    order_id        VARCHAR(36)   NOT NULL,
    refund_no       VARCHAR(32)   NOT NULL UNIQUE,
    reason          VARCHAR(512)  NOT NULL,
    amount          DECIMAL(19,2) NOT NULL,
    status          VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    requester       VARCHAR(64),
    reviewer        VARCHAR(64),
    review_remark   VARCHAR(512),
    completed_at    TIMESTAMP(6),
    created_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_order_id (order_id)
);

-- 4. Outbox event table
CREATE TABLE IF NOT EXISTS order_outbox_event (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    order_id        VARCHAR(36)   NOT NULL,
    topic           VARCHAR(128)  NOT NULL,
    event_type      VARCHAR(64)   NOT NULL,
    payload         TEXT          NOT NULL,
    published       BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at    TIMESTAMP(6),
    INDEX idx_published (published)
);
