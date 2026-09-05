package com.quadrilateral.kudi9ja.domain.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An entry in the customer's notification centre. */
@Entity
@Table(
        name = "notification",
        indexes = {
                @Index(name = "ix_notification_user", columnList = "user_id, created_at"),
                @Index(name = "ix_notification_unread", columnList = "user_id, read_flag")
        })
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 24, updatable = false)
    private NotifyKind kind;

    @Column(name = "title", nullable = false, length = 160, updatable = false)
    private String title;

    @Column(name = "body", nullable = false, length = 1000, updatable = false)
    private String body;

    /** The figure the notification is about, when there is one. */
    @Column(name = "amount", precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    /** "read" is reserved in several dialects, so the column is named around it. */
    @Column(name = "read_flag", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Cleared by the customer. The row stays so a history can be reconstructed. */
    @Column(name = "cleared_at")
    private Instant clearedAt;

    public static Notification of(
            UUID userId, NotifyKind kind, String title, String body, BigDecimal amount) {
        Notification notification = new Notification();
        notification.id = UUID.randomUUID();
        notification.userId = userId;
        notification.kind = kind;
        notification.title = title;
        notification.body = body;
        notification.amount = amount;
        return notification;
    }
}
