package com.quadrilateral.kudi9ja.domain.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One group of notifications a customer has switched off.
 *
 * <p>Only rows for groups they have actually changed. Absence means on, so a
 * customer who has never opened the settings screen hears about their money —
 * the right default, and it keeps the table small.
 *
 * <p>Nothing here can silence a group that {@code NotifyKind} marks
 * non-optional: security alerts and money movements are refused by the service
 * before they reach this table.
 */
@Entity
@Table(
        name = "notification_preference",
        indexes = @Index(name = "ix_pref_user_kind", columnList = "user_id, kind", unique = true))
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreference {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 24)
    private NotifyKind kind;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public static NotificationPreference of(UUID userId, NotifyKind kind) {
        NotificationPreference preference = new NotificationPreference();
        preference.id = UUID.randomUUID();
        preference.userId = userId;
        preference.kind = kind;
        return preference;
    }
}
