CREATE TABLE IF NOT EXISTS notification_log (
    id              CHAR(26)     NOT NULL PRIMARY KEY,
    event_id        VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    recipient_id    VARCHAR(64)  NOT NULL,
    channel         VARCHAR(16)  NOT NULL,
    recipient_addr  VARCHAR(255) NOT NULL,
    template_code   VARCHAR(64)  NOT NULL,
    subject         VARCHAR(255),
    status          VARCHAR(16)  NOT NULL,
    retry_count     INT          NOT NULL DEFAULT 0,
    error_message   VARCHAR(512),
    created_at      DATETIME(3)  NOT NULL,
    sent_at         DATETIME(3),
    UNIQUE KEY uk_event_channel (event_id, channel)
);
