package com.quadrilateral.kudi9ja.domain.admin;

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
 * Someone with access to the admin panel.
 *
 * <p>Granting access creates <b>no account and no password</b>. It means: when
 * somebody signs in with that email, the panel appears. Membership is keyed on
 * the email address and nothing else, and access may only be granted to an
 * email that already belongs to an account — so an owner cannot grant the panel
 * to an address nobody holds by mistyping it.
 *
 * <p>The name and phone are copied off that account for the team list to show.
 * They are never typed.
 *
 * <p>Access is re-checked on every request. Suspending a row takes effect at
 * once, without waiting for a token to lapse.
 */
@Entity
@Table(
        name = "admin_user",
        indexes = {
                @Index(name = "ix_admin_email", columnList = "email", unique = true),
                @Index(name = "ix_admin_user", columnList = "user_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class AdminUser {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** The account this grant belongs to. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** The identity. Access is matched on this alone, lowercased. */
    @Column(name = "email", nullable = false, length = 190)
    private String email;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "phone", length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private AdminRole role = AdminRole.VIEWER;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt = Instant.now();

    @Column(name = "added_by", length = 200)
    private String addedBy;

    /** Suspended rather than removed keeps the history of what they did. */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    public static AdminUser grant(UUID userId, String email, String name, String phone, AdminRole role, String by) {
        AdminUser admin = new AdminUser();
        admin.id = UUID.randomUUID();
        admin.userId = userId;
        admin.email = email.trim().toLowerCase(java.util.Locale.ROOT);
        admin.name = name;
        admin.phone = phone;
        admin.role = role;
        admin.addedBy = by;
        admin.active = true;
        return admin;
    }
}
