package com.quadrilateral.kudi9ja.domain.audit;

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
 * An immutable record of something an admin did.
 *
 * <p><b>Append-only. No edit, no delete, ever.</b> There is no update method on
 * the repository and no setter is called after the row is written; the schema
 * grants the application no DELETE on this table.
 *
 * <p>Every entry carries who did it, what category it falls in, when, and a
 * human-readable detail with the before and after — because the value of an
 * audit log is that a person can read it a year later without reconstructing
 * the state it was written against.
 */
@Entity
@Table(
        name = "audit_entry",
        indexes = {
                @Index(name = "ix_audit_date", columnList = "occurred_at"),
                @Index(name = "ix_audit_category", columnList = "category"),
                @Index(name = "ix_audit_subject", columnList = "subject_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class AuditEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Who did it, by name and email, as it was at the time. */
    @Column(name = "actor", nullable = false, length = 200, updatable = false)
    private String actor;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 24, updatable = false)
    private AuditCategory category;

    @Column(name = "action", nullable = false, length = 120, updatable = false)
    private String action;

    /** Human-readable, with before → after where something changed. */
    @Column(name = "detail", nullable = false, length = 4000, updatable = false)
    private String detail;

    /** The customer, loan or plan the entry is about, when there is one. */
    @Column(name = "subject_id", updatable = false)
    private UUID subjectId;

    @Column(name = "subject_label", length = 200, updatable = false)
    private String subjectLabel;

    /** Where the request came from, for a security investigation. */
    @Column(name = "ip_address", length = 64, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", length = 400, updatable = false)
    private String userAgent;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();
}
