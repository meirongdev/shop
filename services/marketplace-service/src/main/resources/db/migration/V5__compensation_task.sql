CREATE TABLE IF NOT EXISTS compensation_task (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    task_type       VARCHAR(64)   NOT NULL,
    aggregate_id    VARCHAR(64)   NOT NULL,
    payload         TEXT          NOT NULL,
    status          VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    retry_count     INT           NOT NULL DEFAULT 0,
    max_retries     INT           NOT NULL DEFAULT 5,
    next_retry_at   TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_error      TEXT,
    created_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_comp_task_status_next (status, next_retry_at)
) COMMENT 'Persistent outbox for compensatable operations in marketplace-service';
