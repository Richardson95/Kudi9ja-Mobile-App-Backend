package com.quadrilateral.kudi9ja.domain.kyc;

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
 * A six-digit code sent to an email address.
 *
 * <p><b>The code itself is never stored and never returned.</b> Only its hash
 * is kept, and no endpoint echoes it back — the Flutter client displays the
 * code on screen so the flow is testable without a mail server, and that must
 * never ship against a real backend.
 *
 * <p>Single use, short expiry, attempt-capped and rate-limited. Email is the
 * only channel verified: the phone is collected so support can reach the
 * customer, not as a second factor.
 */
@Entity
@Table(
        name = "one_time_code",
        indexes = {
                @Index(name = "ix_otp_target", columnList = "target, purpose"),
                @Index(name = "ix_otp_expires", columnList = "expires_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class OneTimeCode {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** The email the code was sent to, lowercased. */
    @Column(name = "target", nullable = false, length = 190, updatable = false)
    private String target;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32, updatable = false)
    private OtpPurpose purpose;

    /** Argon2id, like every other secret. The code is a six-digit space. */
    @Column(name = "code_hash", nullable = false, length = 300, updatable = false)
    private String codeHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /** Set the moment it is used, so it cannot be used twice. */
    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    /** The account this belongs to once there is one. Null during signup. */
    @Column(name = "user_id")
    private UUID userId;

    public static OneTimeCode issue(
            String target, OtpPurpose purpose, String codeHash, Instant expiresAt, UUID userId) {
        OneTimeCode code = new OneTimeCode();
        code.id = UUID.randomUUID();
        code.target = target.trim().toLowerCase(java.util.Locale.ROOT);
        code.purpose = purpose;
        code.codeHash = codeHash;
        code.expiresAt = expiresAt;
        code.userId = userId;
        return code;
    }

    public boolean isUsable(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public void consume(Instant now) {
        this.consumedAt = now;
    }
}
