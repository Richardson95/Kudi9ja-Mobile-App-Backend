package com.quadrilateral.kudi9ja.web.controller.admin;

import com.quadrilateral.kudi9ja.common.api.PageResponse;
import com.quadrilateral.kudi9ja.domain.admin.AdminAccessService;
import com.quadrilateral.kudi9ja.domain.admin.AdminUser;
import com.quadrilateral.kudi9ja.domain.audit.AuditService;
import com.quadrilateral.kudi9ja.domain.settings.PlatformSettings;
import com.quadrilateral.kudi9ja.domain.settings.SettingsService;
import com.quadrilateral.kudi9ja.web.dto.SettingsDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rates, limits and switches.
 *
 * <p>Four things about this endpoint are load-bearing.
 *
 * <p><b>Changes take effect immediately, and never rewrite history.</b> A
 * running plan or loan keeps the terms it was opened on: every loan stores the
 * rate it was priced at and every savings plan stores its own interest, so
 * moving the savings rate tomorrow changes what the next plan earns and nothing
 * about the ones already running. That invariant lives in the entities, not
 * here, which is what makes it hold.
 *
 * <p><b>The whole document is saved as a new version.</b> Settings are never
 * edited in place. Each save writes a numbered version, and every published
 * version stays readable — so the terms a plan was opened under can always be
 * produced, however many times the card has moved since.
 *
 * <p><b>The change is diffed and audited.</b> What goes to the audit log is not
 * "settings updated" but the fields that moved and what they moved from and to.
 * A change that moves nothing writes no version at all, so the history is a
 * record of decisions rather than of saves.
 *
 * <p><b>Two admins cannot silently overwrite each other.</b> The save carries
 * the version the panel loaded; if somebody else has saved since, the request
 * is refused and the second admin is told to reload rather than quietly
 * discarding the first one's work.
 *
 * <p>Not settable, deliberately: the passcode length (6) and the PIN length
 * (4). Every stored code is a hash of a code that length, so changing either
 * would lock out everyone who already has one.
 */
@RestController
@RequestMapping("/api/v1/admin/settings")
@Tag(name = "Admin — settings", description = "Rates, limits, fees and feature switches")
public class AdminSettingsController {

    private final SettingsService settings;
    private final AdminAccessService access;

    public AdminSettingsController(SettingsService settings, AdminAccessService access) {
        this.settings = settings;
        this.access = access;
    }

    /**
     * The document in force, in full.
     *
     * <p>Including the credit-score coefficients and the loan-offer formula,
     * which the public endpoint withholds. Anyone who can change them needs to
     * see them.
     */
    @GetMapping
    @Operation(summary = "The full settings document in force")
    public PlatformSettings current() {
        access.requireCanView();
        return settings.currentReadOnly();
    }

    @GetMapping("/history")
    @Operation(summary = "Every version, newest first")
    public PageResponse<PlatformSettings> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        access.requireCanView();
        return PageResponse.of(
                settings.history(PageRequest.of(page, Math.min(size, 100))),
                version -> version);
    }

    /**
     * One earlier version.
     *
     * <p>This is how the terms a particular plan or loan was opened under are
     * produced when somebody asks. Kept permanently for that reason.
     */
    @GetMapping("/history/{version}")
    @Operation(summary = "One earlier version of the settings")
    public PlatformSettings version(@PathVariable long version) {
        access.requireCanView();
        return settings.version(version);
    }

    /**
     * Saves a change as a new version.
     *
     * <p>The version, timestamp and author on the incoming document are ignored
     * and stamped on save from the version in force and the caller's own grant,
     * so a client cannot backdate a change or attribute it to somebody else.
     */
    @PutMapping
    @Operation(summary = "Save the settings as a new version. Diffed and audited.")
    public PlatformSettings save(@Valid @RequestBody SettingsDtos.SaveRequest request) {
        AdminUser actor = access.requireCanEditSettings();
        return settings.save(request.settings(), request.expectedVersion(), actorOf(actor));
    }

    /**
     * What the app would see.
     *
     * <p>Here so an admin can check the effect of a change on the customer's
     * screens without installing the app — the same projection the client
     * reads, from the same document.
     */
    @GetMapping("/preview")
    @Operation(summary = "The public projection of the settings, as the app reads it")
    public Map<String, Object> preview() {
        access.requireCanView();
        PlatformSettings current = settings.currentReadOnly();
        return Map.of(
                "version", current.getVersion(),
                "loanRates", current.sortedLoanRates(),
                "savingsAnnualRate", current.getSavingsAnnualRate(),
                "collectionAccount", Map.of(
                        "bank", current.getCompanyBank(),
                        "accountNumber", current.getCompanyAccountNumber(),
                        "accountName", current.getCompanyAccountName()),
                "switches", Map.of(
                        "savings", current.isSavingsEnabled(),
                        "lending", current.isLendingEnabled(),
                        "thrift", current.isThriftEnabled(),
                        "maintenance", current.isMaintenanceMode()));
    }

    private static AuditService.Actor actorOf(AdminUser admin) {
        return new AuditService.Actor(admin.getUserId(), admin.getName(), admin.getEmail());
    }
}
