CREATE TABLE IF NOT EXISTS event_checkpoint (
    id          VARCHAR(36)   NOT NULL PRIMARY KEY,
    event_key   VARCHAR(128)  NOT NULL,
    payload     TEXT          NOT NULL,
    created_at  TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
