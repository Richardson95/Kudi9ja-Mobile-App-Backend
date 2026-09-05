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

/**
 * That a named customer accepted a named version of a document, when, and from
 * what device.
 *
 * <p>The Terms rely on this as evidence under the Evidence Act 2011, so the row
 * records everything that makes it evidence rather than an assertion: which
 * version, the exact timestamp, the device, and the address the acceptance came
 * from. It is written once and never updated.
 */
@Entity
@Table(
        name = "legal_acceptance",
        indexes = {
                @Index(name = "ix_acceptance_user", columnList = "user_id"),
                @Index(name = "ix_acceptance_doc", columnList = "user_id, kind, document_version", unique = true)
        })
@Getter
@Setter
@NoArgsConstructor
public class LegalAcceptance {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16, updatable = false)
    private LegalDocumentKind kind;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "document_version", nullable = false, length = 24, updatable = false)
    private String documentVersion;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt = Instant.now();

    /** What the customer accepted it on, as the device reported itself. */
    @Column(name = "device", length = 300, updatable = false)
    private String device;

    @Column(name = "ip_address", length = 64, updatable = false)
    private String ipAddress;

    public static LegalAcceptance of(
            UUID userId, LegalDocument document, String device, String ipAddress) {
        LegalAcceptance acceptance = new LegalAcceptance();
        acceptance.id = UUID.randomUUID();
        acceptance.userId = userId;
        acceptance.kind = document.getKind();
        acceptance.documentId = document.getId();
        acceptance.documentVersion = document.getVersion();
        acceptance.device = device;
        acceptance.ipAddress = ipAddress;
        return acceptance;
    }
}
