package com.quadrilateral.kudi9ja.web.controller;

import com.quadrilateral.kudi9ja.common.error.ApiException;
import com.quadrilateral.kudi9ja.config.Kudi9jaProperties;
import com.quadrilateral.kudi9ja.domain.legal.LegalDocument;
import com.quadrilateral.kudi9ja.domain.legal.LegalDocumentKind;
import com.quadrilateral.kudi9ja.domain.legal.LegalService;
import com.quadrilateral.kudi9ja.domain.settings.SettingsService;
import com.quadrilateral.kudi9ja.domain.user.NigerianStates;
import com.quadrilateral.kudi9ja.integration.bank.BankDirectory;
import com.quadrilateral.kudi9ja.web.dto.SettingsDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the app can read before anybody has an account.
 *
 * <p>Four things, and each is here for a reason rather than by omission. The
 * <b>rates and limits</b>, because the savings calculator and the loan card are
 * on the screens somebody looks at while deciding whether to sign up at all.
 * The <b>legal documents</b>, for the same reason and a stronger one: nobody
 * can meaningfully accept terms they had to open an account to read. The
 * <b>banks</b> and <b>states</b>, because both are needed to fill in the sign-up
 * form itself.
 *
 * <p>Nothing here is customer-specific and nothing here moves money, which is
 * what makes leaving it open safe.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Public", description = "Rates, documents and reference data, readable without an account")
public class PublicController {

    private final SettingsService settings;
    private final LegalService legal;
    private final Kudi9jaProperties properties;

    public PublicController(
            SettingsService settings, LegalService legal, Kudi9jaProperties properties) {
        this.settings = settings;
        this.legal = legal;
        this.properties = properties;
    }

    /**
     * The rates and limits the app displays.
     *
     * <p>Deliberately excluded: the credit-score coefficients and the loan-offer
     * formula. Those are the inputs to a lending decision, and publishing them
     * would be publishing how to game it. They are visible in the admin panel to
     * anyone who can change them, which is the audience that needs them.
     */
    @GetMapping("/settings/public")
    @Operation(summary = "Rates, limits, switches and the collection account")
    public SettingsDtos.PublicSettings publicSettings() {
        Kudi9jaProperties.Company company = properties.company();
        return SettingsDtos.PublicSettings.from(
                settings.currentReadOnly(),
                new SettingsDtos.Company(
                        company.legalName(),
                        company.rcNumber(),
                        company.productName(),
                        company.supportEmail(),
                        company.legalEmail(),
                        company.privacyEmail(),
                        company.supportPhone(),
                        company.whatsapp()));
    }

    @GetMapping("/legal")
    @Operation(summary = "The current version of all three documents")
    public Map<String, LegalDocumentResponse> allDocuments() {
        return legal.currentAll().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> entry.getKey().name().toLowerCase(Locale.ROOT),
                        entry -> LegalDocumentResponse.from(entry.getValue())));
    }

    /**
     * One document, current or at a named version.
     *
     * <p>The version parameter is not a convenience. The server records which
     * version each customer accepted, and a record of that kind is worth little
     * if the document it names can no longer be produced — so every published
     * version stays readable, permanently.
     */
    @GetMapping("/legal/{kind}")
    @Operation(summary = "The Terms, the Privacy Policy or the Lending Agreement")
    public LegalDocumentResponse document(
            @PathVariable String kind,
            @RequestParam(required = false) String version) {

        LegalDocumentKind documentKind = parseKind(kind);
        return LegalDocumentResponse.from(version == null || version.isBlank()
                ? legal.current(documentKind)
                : legal.version(documentKind, version));
    }

    @GetMapping("/legal/{kind}/versions")
    @Operation(summary = "Every published version of one document")
    public List<LegalDocumentResponse> versions(@PathVariable String kind) {
        return legal.history(parseKind(kind)).stream()
                .map(LegalDocumentResponse::from)
                .toList();
    }

    /**
     * Documents that are published but not yet in force.
     *
     * <p>The company gives thirty days' notice before a new charge, an increase
     * or a material change takes effect. This is what makes that notice visible
     * rather than merely given.
     */
    @GetMapping("/legal/upcoming")
    @Operation(summary = "Published documents that take effect later")
    public List<LegalDocumentResponse> upcoming() {
        return legal.upcoming().stream().map(LegalDocumentResponse::from).toList();
    }

    @GetMapping("/banks")
    @Operation(summary = "The banks a payout account may be held at")
    public List<BankDirectory.Bank> banks() {
        return BankDirectory.all();
    }

    @GetMapping("/states")
    @Operation(summary = "The 36 states and the FCT")
    public List<String> states() {
        return NigerianStates.all();
    }

    private static LegalDocumentKind parseKind(String raw) {
        try {
            return LegalDocumentKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.validation(
                    "There are three documents: terms, privacy and lending.");
        }
    }

    /**
     * A document, with its body.
     *
     * <p>The body travels as the structured JSON the app renders, rather than as
     * prose it would have to parse. The documents live as data in the client for
     * the same reason, and keeping the shape means the app renders a new version
     * without shipping a release.
     */
    public record LegalDocumentResponse(
            String kind,
            String title,
            String shortTitle,
            String version,
            String summary,
            int readMinutes,
            java.time.Instant effectiveFrom,
            java.time.Instant publishedAt,
            boolean inForce,
            String changeSummary,
            Object body) {

        public static LegalDocumentResponse from(LegalDocument document) {
            return new LegalDocumentResponse(
                    document.getKind().name().toLowerCase(Locale.ROOT),
                    document.getTitle(),
                    document.getShortTitle(),
                    document.getVersion(),
                    document.getSummary(),
                    document.getReadMinutes(),
                    document.getEffectiveFrom(),
                    document.getPublishedAt(),
                    document.isInForce(java.time.Instant.now()),
                    document.getChangeSummary(),
                    readBody(document));
        }

        private static Object readBody(LegalDocument document) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(document.getBodyJson());
            } catch (Exception e) {
                // A document that cannot be parsed is still a document that has
                // to be readable, so the raw text goes out rather than an error.
                return document.getBodyJson();
            }
        }
    }
}
