package com.quadrilateral.kudi9ja.domain.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One phone a customer has Kudi9ja installed on.
 *
 * <p>A customer may hold several. Somebody with a phone and a tablet expects
 * both to buzz, and somebody who has just replaced a handset expects the old
 * one to stop — so a token is registered per device and dropped when it stops
 * working, rather than one token being kept per account.
 *
 * <p>The token is issued by Firebase and belongs to the app installation, not
 * to the person. It rotates on its own: on a reinstall, on a restore to a new
 * handset, and sometimes for no visible reason. That is why the app re-registers
 * on every sign-in rather than once at setup, and why a token that Firebase
 * later rejects is deleted rather than retried.
 *
 * <p>It is not a credential and grants nothing. Anybody holding it could send
 * this phone a notification if they also held the Firebase key; they could not
 * read anything, and nothing is authorised on the strength of it.
 */
@Entity
@Table(
        name = "device_token",
        indexes = {
                @Index(name = "ix_device_token", columnList = "token", unique = true),
                @Index(name = "ix_device_user", columnList = "user_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class DeviceToken {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** The Firebase registration token. Long, opaque, and rotates on its own. */
    @Column(name = "token", nullable = false, length = 512)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 16)
    private DevicePlatform platform;

    /** What the customer sees on their own security screen. */
    @Column(name = "device_label", length = 200)
    private String deviceLabel;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt = Instant.now();

    /**
     * Touched whenever the app re-registers, which it does on every sign-in.
     *
     * <p>What makes it possible to drop tokens for handsets nobody has opened
     * in months, rather than sending to an ever-growing list of phones that no
     * longer exist.
     */
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();

    public static DeviceToken register(
            UUID userId, String token, DevicePlatform platform, String deviceLabel) {

        DeviceToken device = new DeviceToken();
        device.id = UUID.randomUUID();
        device.userId = userId;
        device.token = token;
        device.platform = platform;
        device.deviceLabel = deviceLabel;
        return device;
    }

    public void touch(UUID owner, String label) {
        // A handset that changed hands keeps its token but not its owner: the
        // person who signed in last is the person the phone now belongs to.
        this.userId = owner;
        this.deviceLabel = label;
        this.lastSeenAt = Instant.now();
    }
}
