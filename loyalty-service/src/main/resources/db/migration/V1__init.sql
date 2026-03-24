-- V1: loyalty-service schema initialization

CREATE TABLE IF NOT EXISTS loyalty_account (
    player_id     VARCHAR(64)  NOT NULL PRIMARY KEY,
    total_points  BIGINT       NOT NULL DEFAULT 0,
    used_points   BIGINT       NOT NULL DEFAULT 0,
    balance       BIGINT       NOT NULL DEFAULT 0,
    tier          VARCHAR(20)  NOT NULL DEFAULT 'SILVER',
    tier_points   BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE TABLE IF NOT EXISTS loyalty_transaction (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    player_id     VARCHAR(64)  NOT NULL,
    type          VARCHAR(32)  NOT NULL,
    source        VARCHAR(32)  NOT NULL,
    amount        BIGINT       NOT NULL,
    balance_after BIGINT       NOT NULL,
    reference_id  VARCHAR(64),
    remark        VARCHAR(256),
    expire_at     DATE,
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_player_created (player_id, created_at DESC)
);

CREATE TABLE IF NOT EXISTS loyalty_checkin (
    id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    player_id      VARCHAR(64)  NOT NULL,
    checkin_date   DATE         NOT NULL,
    streak_day     INT          NOT NULL DEFAULT 1,
    points_earned  BIGINT       NOT NULL,
    is_makeup      TINYINT(1)   NOT NULL DEFAULT 0,
    makeup_cost    BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_player_date (player_id, checkin_date)
);

CREATE TABLE IF NOT EXISTS loyalty_reward_item (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    name            VARCHAR(128)  NOT NULL,
    description     VARCHAR(512),
    type            VARCHAR(32)   NOT NULL,
    points_required BIGINT        NOT NULL,
    stock           INT           NOT NULL DEFAULT 0,
    image_url       VARCHAR(512),
    active          TINYINT(1)    NOT NULL DEFAULT 1,
    sort_order      INT           NOT NULL DEFAULT 0,
    created_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE TABLE IF NOT EXISTS loyalty_redemption (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    player_id       VARCHAR(64)   NOT NULL,
    reward_item_id  VARCHAR(36)   NOT NULL,
    reward_name     VARCHAR(128)  NOT NULL,
    points_spent    BIGINT        NOT NULL,
    quantity        INT           NOT NULL DEFAULT 1,
    status          VARCHAR(32)   NOT NULL,
    type            VARCHAR(32)   NOT NULL,
    coupon_code     VARCHAR(64),
    order_id        VARCHAR(36),
    remark          VARCHAR(256),
    created_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_player (player_id, created_at DESC)
);

CREATE TABLE IF NOT EXISTS loyalty_earn_rule (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    source          VARCHAR(32)   NOT NULL UNIQUE,
    points_formula  VARCHAR(32)   NOT NULL,
    base_value      BIGINT        NOT NULL,
    max_per_day     BIGINT,
    active          TINYINT(1)    NOT NULL DEFAULT 1,
    updated_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

-- Seed earn rules
INSERT INTO loyalty_earn_rule (id, source, points_formula, base_value, max_per_day, active) VALUES
('rule-purchase', 'PURCHASE',  'PER_DOLLAR', 10,   NULL, 1),
('rule-checkin',  'CHECKIN',   'FIXED',       5,   1,    1),
('rule-review',   'REVIEW',    'FIXED',      20,   1,    1),
('rule-share',    'SHARE',     'FIXED',       5,   3,    1),
('rule-register', 'REGISTER',  'FIXED',     100,   NULL, 1);

CREATE TABLE IF NOT EXISTS onboarding_task_template (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    task_key      VARCHAR(64)   NOT NULL UNIQUE,
    title         VARCHAR(128)  NOT NULL,
    description   VARCHAR(256),
    points_reward BIGINT        NOT NULL,
    sort_order    INT           NOT NULL DEFAULT 0,
    active        TINYINT(1)    NOT NULL DEFAULT 1
);

-- Seed onboarding tasks
INSERT INTO onboarding_task_template (id, task_key, title, description, points_reward, sort_order) VALUES
('task-01', 'COMPLETE_PROFILE', 'Complete Your Profile',  'Add avatar and nickname',      20, 1),
('task-02', 'FIRST_CHECKIN',    'First Check-in',         'Check in for the first time',  10, 2),
('task-03', 'FIRST_ADD_CART',   'Add to Cart',            'Add your first item to cart',  10, 3),
('task-04', 'FIRST_ORDER',      'First Order',            'Complete your first purchase',  50, 4),
('task-05', 'FIRST_REVIEW',     'Write a Review',         'Write your first product review', 20, 5),
('task-06', 'FIRST_REFERRAL',   'Invite a Friend',        'Invite a friend to register',  30, 6),
('task-07', 'BIND_PHONE',       'Bind Phone Number',      'Link your phone number',       10, 7);

CREATE TABLE IF NOT EXISTS onboarding_task_progress (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    player_id     VARCHAR(64)   NOT NULL,
    task_key      VARCHAR(64)   NOT NULL,
    status        VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    points_issued BIGINT        NOT NULL DEFAULT 0,
    completed_at  TIMESTAMP(6),
    expire_at     TIMESTAMP(6)  NOT NULL,
    created_at    TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_player_task (player_id, task_key),
    INDEX idx_player_expire (player_id, expire_at)
);
