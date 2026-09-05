package com.quadrilateral.kudi9ja.domain.legal;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One published version of a legal document.
 *
 * <p>Versions are never edited and never removed. A customer accepted a
 * particular wording on a particular day, and the Terms rely on that record as
 * evidence under the Evidence Act 2011 — which is worth nothing if the wording
 * they accepted can be quietly rewritten afterwards.
 *
 * <p>Publishing a new version does not take effect immediately either. A
 * material change, a new charge or an increase requires thirty days' notice,
 * so a version carries the date it becomes effective and the date it was
 * announced.
 *
 * <p>The body is the same block structure the app renders, held as JSON so the
 * client keeps one renderer and the server stays out of the business of
 * formatting prose.
 */
@Entity
@Table(
        name = "legal_document",
        indexes = {
                @Index(name = "ix_legal_kind_version", columnList = "kind, version", unique = true),
                @Index(name = "ix_legal_effective", columnList = "kind, effective_from")
        })
@Getter
@Setter
@NoArgsConstructor
public class LegalDocument {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16, updatable = false)
    private LegalDocumentKind kind;

    /** Bumped whenever the wording changes in a way customers must be told about. */
    @Column(name = "version", nullable = false, length = 24, updatable = false)
    private String version;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "short_title", nullable = false, length = 120)
    private String shortTitle;

    /** One or two sentences a customer can read instead of the whole thing. */
    @Column(name = "summary", nullable = false, length = 1000)
    private String summary;

    @Column(name = "read_minutes", nullable = false)
    private int readMinutes;

    /**
     * The block structure, as JSON.
     *
     * <p>Mapped to {@code text} rather than left as a bare {@code @Lob}. On
     * PostgreSQL a {@code @Lob String} becomes an {@code oid} — a pointer into
     * the large-object table — which needs the LOB API and an open transaction
     * to read, and fails at runtime through a plain String accessor. It works
     * on H2, so the difference shows up first in a deployment rather than
     * locally, which is the worst place to find it.
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "body_json", nullable = false, columnDefinition = "text")
    private String bodyJson;

    /** When this wording starts to bind. */
    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    /** When customers were told it was coming, for the notice period. */
    @Column(name = "announced_at")
    private Instant announcedAt;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt = Instant.now();

    @Column(name = "published_by", length = 200)
    private String publishedBy;

    /**
     * What changed and why, in a sentence. Shown to customers alongside the
     * notice, because "the Terms have been updated" tells nobody anything.
     */
    @Column(name = "change_summary", length = 2000)
    private String changeSummary;

    public boolean isInForce(Instant now) {
        return !effectiveFrom.isAfter(now);
    }
}
