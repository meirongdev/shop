-- V1: activity-service schema initialization

CREATE TABLE IF NOT EXISTS activity_game (
    id                    VARCHAR(36)  NOT NULL PRIMARY KEY,
    type                  VARCHAR(32)  NOT NULL,
    name                  VARCHAR(128) NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    config                JSON,
    start_at              TIMESTAMP(6),
    end_at                TIMESTAMP(6),
    per_user_daily_limit  INT          NOT NULL DEFAULT 1,
    per_user_total_limit  INT          NOT NULL DEFAULT 3,
    entry_condition       JSON,
    participant_count     INT          NOT NULL DEFAULT 0,
    created_by            VARCHAR(64),
    created_at            TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_status_time (status, start_at, end_at)
);

CREATE TABLE IF NOT EXISTS activity_reward_prize (
    id                  VARCHAR(36)   NOT NULL PRIMARY KEY,
    game_id             VARCHAR(36)   NOT NULL,
    name                VARCHAR(128)  NOT NULL,
    type                VARCHAR(32)   NOT NULL,
    value               DECIMAL(19,2) NOT NULL DEFAULT 0,
    coupon_template_id  VARCHAR(36),
    total_stock         INT           NOT NULL DEFAULT -1,
    remaining_stock     INT           NOT NULL DEFAULT -1,
    probability         DECIMAL(9,8)  NOT NULL DEFAULT 0.0,
    display_order       INT           NOT NULL DEFAULT 0,
    image_url           VARCHAR(512),
    FOREIGN KEY (game_id) REFERENCES activity_game(id),
    INDEX idx_game (game_id)
);

CREATE TABLE IF NOT EXISTS activity_participation (
    id                  VARCHAR(36)   NOT NULL PRIMARY KEY,
    game_id             VARCHAR(36)   NOT NULL,
    game_type           VARCHAR(32)   NOT NULL,
    player_id           VARCHAR(64),
    session_id          VARCHAR(128),
    ip_address          VARCHAR(64),
    device_fingerprint  VARCHAR(256),
    participated_at     TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    result              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    prize_id            VARCHAR(36),
    prize_snapshot      JSON,
    reward_status       VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    reward_ref          VARCHAR(128),
    extra_data          JSON,
    INDEX idx_game_player (game_id, player_id),
    INDEX idx_reward_status (reward_status),
    INDEX idx_participated_at (participated_at)
);

CREATE TABLE IF NOT EXISTS activity_outbox_event (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
    game_id      VARCHAR(36),
    topic        VARCHAR(128) NOT NULL,
    event_type   VARCHAR(64)  NOT NULL,
    payload      TEXT         NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at TIMESTAMP(6),
    INDEX idx_status (status)
);
