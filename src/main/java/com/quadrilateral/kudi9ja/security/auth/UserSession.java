package com.quadrilateral.kudi9ja.security.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One sign-in, from the moment the password was accepted to the moment the
 * session was signed out, revoked or lapsed.
 *
 * <p>Tokens carry this row's id, so revoking a session takes effect on the
 * next request rather than at the token's expiry. That is what makes a
 * sign-out real, and what lets a password change, an admin freeze or a
 * suspected token theft end every live session at once.
 *
 * <p>The refresh token is stored as a hash of its id and rotated on every
 * exchange. Presenting a refresh token that has already been used is treated
 * as theft: the whole session is revoked rather than merely refused, because
 * the legitimate holder and the thief are now indistinguishable.
 */
@Entity
@Table(
        name = "user_session",
        indexes = {
                @Index(name = "ix_session_user", columnList = "user_id"),
                @Index(name = "ix_session_expiry", columnList = "refresh_expires_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class UserSession {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** The id of the refresh token currently valid for this session. */
    @Column(name = "refresh_token_id", length = 64)
    private String refreshTokenId;

    @Column(name = "refresh_expires_at")
    private Instant refreshExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Why it ended, for the customer's own security screen. */
    @Column(name = "revoked_reason", length = 200)
    private String revokedReason;

    /** What signed in, so a customer can recognise a session that is not theirs. */
    @Column(name = "device_label", length = 200)
    private String deviceLabel;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    public static UserSession open(UUID userId, String deviceLabel, String ipAddress) {
        UserSession session = new UserSession();
        session.id = UUID.randomUUID();
        session.userId = userId;
        session.deviceLabel = deviceLabel;
        session.ipAddress = ipAddress;
        session.lastSeenAt = session.createdAt;
        return session;
    }

    public boolean isLive(Instant now) {
        return revokedAt == null && (refreshExpiresAt == null || refreshExpiresAt.isAfter(now));
    }

    public void revoke(String reason, Instant now) {
        this.revokedAt = now;
        this.revokedReason = reason;
        this.refreshTokenId = null;
    }
}
