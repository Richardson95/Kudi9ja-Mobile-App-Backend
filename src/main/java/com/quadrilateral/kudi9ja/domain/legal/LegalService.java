package com.quadrilateral.kudi9ja.domain.legal;

import com.quadrilateral.kudi9ja.common.error.ApiException;
import com.quadrilateral.kudi9ja.common.error.ErrorCode;
import com.quadrilateral.kudi9ja.config.Kudi9jaProperties;
import com.quadrilateral.kudi9ja.domain.audit.AuditCategory;
import com.quadrilateral.kudi9ja.domain.audit.AuditService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serves the binding documents and records who accepted which version.
 *
 * <p>Two obligations meet here. The customer must be able to read what binds
 * them before they agree to it, so the documents are open without a token. And
 * the company must be able to show what a given customer accepted, on what day,
 * from what device — which is what makes the acceptance evidence rather than an
 * assertion.
 */
@Service
public class LegalService {

    private static final Logger log = LoggerFactory.getLogger(LegalService.class);

    private final LegalDocumentRepository documents;
    private final LegalAcceptanceRepository acceptances;
    private final AuditService audit;
    private final Kudi9jaProperties properties;

    public LegalService(
            LegalDocumentRepository documents,
            LegalAcceptanceRepository acceptances,
            AuditService audit,
            Kudi9jaProperties properties) {
        this.documents = documents;
        this.acceptances = acceptances;
        this.audit = audit;
        this.properties = properties;
    }

    /** The wording that binds right now. */
    @Transactional(readOnly = true)
    public LegalDocument current(LegalDocumentKind kind) {
        List<LegalDocument> inForce = documents.inForce(kind, Instant.now());
        if (inForce.isEmpty()) {
            throw ApiException.notFound("That document");
        }
        return inForce.get(0);
    }

    @Transactional(readOnly = true)
    public Map<LegalDocumentKind, LegalDocument> currentAll() {
        Map<LegalDocumentKind, LegalDocument> all = new EnumMap<>(LegalDocumentKind.class);
        for (LegalDocumentKind kind : LegalDocumentKind.values()) {
            documents.inForce(kind, Instant.now()).stream().findFirst()
                    .ifPresent(document -> all.put(kind, document));
        }
        return all;
    }

    @Transactional(readOnly = true)
    public LegalDocument version(LegalDocumentKind kind, String version) {
        return documents.findByKindAndVersion(kind, version)
                .orElseThrow(() -> ApiException.notFound("That version of the document"));
    }

    @Transactional(readOnly = true)
    public List<LegalDocument> history(LegalDocumentKind kind) {
        return documents.findByKindOrderByEffectiveFromDesc(kind);
    }

    /** Versions announced and waiting out their notice period. */
    @Transactional(readOnly = true)
    public List<LegalDocument> upcoming() {
        return documents.upcoming(Instant.now());
    }

    /**
     * Records that a customer accepted every document in force.
     *
     * <p>Called once, at the end of signup, against the versions the review
     * step actually showed them. Accepting a version that has since been
     * superseded is refused: the customer must agree to what binds them, not to
     * what used to.
     */
    @Transactional
    public List<LegalAcceptance> acceptAll(
            UUID userId, Map<LegalDocumentKind, String> acceptedVersions, String device, String ipAddress) {

        Map<LegalDocumentKind, LegalDocument> inForce = currentAll();
        for (LegalDocumentKind kind : LegalDocumentKind.values()) {
            LegalDocument document = inForce.get(kind);
            if (document == null) {
                throw new ApiException(
                        ErrorCode.INTERNAL, "The " + kind.defaultTitle() + " has not been published.");
            }
            String accepted = acceptedVersions.get(kind);
            if (accepted == null) {
                throw new ApiException(
                        ErrorCode.LEGAL_ACCEPTANCE_REQUIRED,
                        "You have to accept the " + document.getTitle() + " to open an account.");
            }
            if (!accepted.equals(document.getVersion())) {
                throw new ApiException(
                        ErrorCode.LEGAL_ACCEPTANCE_REQUIRED,
                        "The " + document.getTitle() + " has changed since you opened it. "
                                + "Read the current version and accept that one.",
                        Map.of("document", kind.id(), "currentVersion", document.getVersion()));
            }
        }

        return inForce.values().stream()
                .map(document -> acceptances.save(
                        LegalAcceptance.of(userId, document, device, ipAddress)))
                .toList();
    }

    /**
     * Records acceptance of one document, for a customer meeting a new version
     * after a change notice.
     */
    @Transactional
    public LegalAcceptance accept(
            UUID userId, LegalDocumentKind kind, String version, String device, String ipAddress) {
        LegalDocument document = version(kind, version);
        if (acceptances.existsByUserIdAndKindAndDocumentVersion(userId, kind, version)) {
            return acceptances.findFirstByUserIdAndKindOrderByAcceptedAtDesc(userId, kind).orElseThrow();
        }
        return acceptances.save(LegalAcceptance.of(userId, document, device, ipAddress));
    }

    @Transactional(readOnly = true)
    public List<LegalAcceptance> acceptancesFor(UUID userId) {
        return acceptances.findByUserIdOrderByAcceptedAtDesc(userId);
    }

    /**
     * Which documents this customer has not accepted the current version of.
     * The app shows these on next open rather than blocking the account.
     */
    @Transactional(readOnly = true)
    public List<LegalDocument> outstandingFor(UUID userId) {
        return currentAll().values().stream()
                .filter(document -> !acceptances.existsByUserIdAndKindAndDocumentVersion(
                        userId, document.getKind(), document.getVersion()))
                .toList();
    }

    /**
     * Publishes a new version.
     *
     * <p>A material change, a new charge or an increase needs thirty days'
     * notice, so an effective date inside that window is refused unless the
     * publisher marks the change as immaterial — a correction of a typo does
     * not need a month's warning, and pretending it does trains everyone to
     * ignore the notice.
     */
    @Transactional
    public LegalDocument publish(
            LegalDocumentKind kind,
            String version,
            String title,
            String shortTitle,
            String summary,
            int readMinutes,
            String bodyJson,
            Instant effectiveFrom,
            String changeSummary,
            boolean material,
            AuditService.Actor actor) {

        if (documents.existsByKindAndVersion(kind, version)) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    "Version " + version + " of the " + kind.defaultTitle() + " already exists. "
                            + "Published wording is never edited; publish a new version instead.");
        }

        Instant now = Instant.now();
        int noticeDays = properties.compliance().changeNoticeDays();
        if (material && effectiveFrom.isBefore(now.plus(noticeDays, ChronoUnit.DAYS))) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "A material change needs " + noticeDays + " days' notice. "
                            + "Set an effective date at least that far out.",
                    Map.of("earliestEffectiveFrom", now.plus(noticeDays, ChronoUnit.DAYS)));
        }

        LegalDocument document = new LegalDocument();
        document.setId(UUID.randomUUID());
        document.setKind(kind);
        document.setVersion(version);
        document.setTitle(title);
        document.setShortTitle(shortTitle);
        document.setSummary(summary);
        document.setReadMinutes(readMinutes);
        document.setBodyJson(bodyJson);
        document.setEffectiveFrom(effectiveFrom);
        document.setAnnouncedAt(now);
        document.setPublishedAt(now);
        document.setPublishedBy(actor == null ? "System" : actor.describe());
        document.setChangeSummary(changeSummary);

        LegalDocument saved = documents.save(document);
        audit.record(
                actor,
                AuditCategory.COMPLIANCE,
                "Legal document published",
                kind.defaultTitle() + " version " + version + " published, effective "
                        + effectiveFrom + ". " + (changeSummary == null ? "" : changeSummary));
        log.info("Published {} version {} effective {}", kind, version, effectiveFrom);
        return saved;
    }
}
