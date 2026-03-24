CREATE TABLE IF NOT EXISTS activity_virtual_farm (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    game_id       VARCHAR(36)   NOT NULL,
    player_id     VARCHAR(64)   NOT NULL,
    stage         INT           NOT NULL DEFAULT 1,
    progress      INT           NOT NULL DEFAULT 0,
    max_stage     INT           NOT NULL,
    max_progress  INT           NOT NULL,
    last_water_at TIMESTAMP(6),
    harvested_at  TIMESTAMP(6),
    created_at    TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_activity_virtual_farm_game_player (game_id, player_id),
    INDEX idx_virtual_farm_game (game_id, player_id)
);
