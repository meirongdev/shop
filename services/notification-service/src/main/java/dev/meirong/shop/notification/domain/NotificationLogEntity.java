package dev.meirong.shop.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "notification_log")
public class NotificationLogEntity {

    @Id
    @Column(length = 26, columnDefinition = "CHAR(26)")
    private String id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "recipient_id", nullable = false, length = 64)
    private String recipientId;

    @Column(nullable = false, length = 16)
    private String channel;

    @Column(name = "recipient_addr", nullable = false, length = 255)
    private String recipientAddr;

    @Column(name = "template_code", nullable = false, length = 64)
    private String templateCode;

    @Column(length = 255)
    private String subject;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected NotificationLogEntity() {
    }

    public NotificationLogEntity(String id, String eventId, String eventType,
                                  String recipientId, String channel,
                                  String recipientAddr, String templateCode,
                                  String subject) {
        this.id = id;
        this.eventId = eventId;
        this.eventType = eventType;
        this.recipientId = recipientId;
        this.channel = channel;
        this.recipientAddr = recipientAddr;
        this.templateCode = templateCode;
        this.subject = subject;
        this.status = "PENDING";
        this.retryCount = 0;
        this.createdAt = Instant.now();
    }

    public void markSent() {
        this.status = "SENT";
        this.sentAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.status = "FAILED";
        this.retryCount++;
        this.errorMessage = errorMessage != null && errorMessage.length() > 512
                ? errorMessage.substring(0, 512) : errorMessage;
    }

    public void resetToPending() {
        this.status = "PENDING";
    }

    public String getId() { return id; }
    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getRecipientId() { return recipientId; }
    public String getChannel() { return channel; }
    public String getRecipientAddr() { return recipientAddr; }
    public String getTemplateCode() { return templateCode; }
    public String getSubject() { return subject; }
    public String getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
}
