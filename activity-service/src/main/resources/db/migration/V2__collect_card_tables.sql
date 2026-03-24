CREATE TABLE IF NOT EXISTS activity_collect_card_def (
    id          VARCHAR(36)   NOT NULL PRIMARY KEY,
    game_id     VARCHAR(36)   NOT NULL,
    card_name   VARCHAR(64)   NOT NULL,
    rarity      VARCHAR(16)   NOT NULL,
    probability DECIMAL(9,8)  NOT NULL,
    created_at  TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_collect_card_game (game_id)
);

CREATE TABLE IF NOT EXISTS activity_player_card (
    id          VARCHAR(36)   NOT NULL PRIMARY KEY,
    game_id     VARCHAR(36)   NOT NULL,
    player_id   VARCHAR(64)   NOT NULL,
    card_id     VARCHAR(36)   NOT NULL,
    source      VARCHAR(32)   NOT NULL,
    created_at  TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_player_card_game (game_id, player_id),
    INDEX idx_player_card_lookup (game_id, player_id, card_id)
);
