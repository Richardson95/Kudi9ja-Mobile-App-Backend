package com.quadrilateral.kudi9ja.web.controller.admin;

import com.quadrilateral.kudi9ja.common.api.PageResponse;
import com.quadrilateral.kudi9ja.common.error.ApiException;
import com.quadrilateral.kudi9ja.domain.admin.AdminAccessService;
import com.quadrilateral.kudi9ja.domain.admin.AdminUser;
import com.quadrilateral.kudi9ja.domain.audit.AuditService;
import com.quadrilateral.kudi9ja.domain.payin.DepositStatus;
import com.quadrilateral.kudi9ja.domain.payin.PayInClaim;
import com.quadrilateral.kudi9ja.domain.payin.PayInService;
import com.quadrilateral.kudi9ja.domain.payin.UnmatchedPayIn;
import com.quadrilateral.kudi9ja.domain.payin.UnmatchedStatus;
import com.quadrilateral.kudi9ja.web.dto.PayInDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Confirming money in.
 *
 * <p>This is the <b>only</b> place a wallet is credited from outside. No card,
 * no USSD, no instant credit: an admin matches the claim's reference against
 * the bank statement, looks at the receipt, and confirms — or rejects it with a
 * reason. Until then the claim is pending and the balance has not moved.
 *
 * <p>Confirming a claim whose purpose is a loan repayment credits the wallet
 * <b>and</b> applies the amount to the named loan, so both legs appear in the
 * ledger rather than the money arriving and quietly vanishing.
 *
 * <p>The receipt is served over a <b>signed URL that expires</b>, and every
 * view is written to the audit log. A receipt carries a customer's bank details
 * and their name, and the Privacy Policy commits to being able to say who
 * looked at one.
 *
 * <p>The second half of this controller handles money that arrived <b>without</b>
 * an owner: held while it is traced, matched to a customer if it can be, and
 * otherwise returned to source after thirty days by the daily job. That window
 * is in the Terms.
 */
@RestController
@RequestMapping("/api/v1/admin/payins")
@Tag(name = "Admin — pay-ins", description = "Confirming bank transfers into wallets")
public class AdminPayInController {

    private final PayInService payIns;
    private final AdminAccessService access;

    public AdminPayInController(PayInService payIns, AdminAccessService access) {
        this.payIns = payIns;
        this.access = access;
    }

    /**
     * The review queue.
     *
     * <p>Defaults to pending, oldest first, because the promise is a review
     * within one working day and the oldest claim is the one closest to
     * breaking it.
     */
    @GetMapping
    @Operation(summary = "The pay-in queue, filterable by status")
    public PageResponse<PayInDtos.AdminClaimResponse> queue(
            @RequestParam(defaultValue = "PENDING") DepositStatus status,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        access.requireCanView();
        return PageResponse.of(
                payIns.queue(status, query, PageRequest.of(page, Math.min(size, 100))),
                payIns::toAdminResponse);
    }

    @GetMapping("/{claimId}")
    @Operation(summary = "One claim, with a signed receipt URL")
    public PayInDtos.AdminClaimResponse claim(@PathVariable UUID claimId) {
        access.requireCanView();
        return payIns.toAdminResponse(payIns.get(claimId));
    }

    /**
     * A fresh signed URL for the receipt.
     *
     * <p>Separate from reading the claim, and audited, so opening the queue is
     * not recorded as having looked at everybody's bank details.
     */
    @PostMapping("/{claimId}/receipt")
    @Operation(summary = "Mint a signed, expiring URL for the receipt. Audited.")
    public Map<String, String> receiptUrl(@PathVariable UUID claimId) {
        AdminUser actor = access.requireCanView();
        return Map.of("url", payIns.receiptUrl(claimId, actorOf(actor)));
    }

    /**
     * Confirms a payment against the statement, and credits the wallet.
     *
     * <p>The one action in this system that creates money in a wallet from
     * outside it, which is why it sits behind the payments permission and is
     * written to the audit log with the admin who took it.
     */
    @PostMapping("/{claimId}/confirm")
    @Operation(summary = "Confirm the payment and credit the wallet")
    public PayInDtos.AdminClaimResponse confirm(
            @PathVariable UUID claimId,
            @RequestBody(required = false) PayInDtos.ReviewRequest request) {

        AdminUser actor = access.requireCanApprovePayments();
        PayInClaim claim = payIns.confirm(claimId, actorOf(actor), request == null ? null : request.note());
        return payIns.toAdminResponse(claim);
    }

    /**
     * Rejects a claim.
     *
     * <p>The reason is mandatory and it reaches the customer. A claim that
     * simply stopped being pending, with nothing said, is how somebody comes to
     * believe their money has been taken.
     */
    @PostMapping("/{claimId}/reject")
    @Operation(summary = "Reject the claim. The reason is required and is sent to the customer.")
    public PayInDtos.AdminClaimResponse reject(
            @PathVariable UUID claimId,
            @Valid @RequestBody PayInDtos.ReviewRequest request) {

        AdminUser actor = access.requireCanApprovePayments();
        if (request == null || request.note() == null || request.note().isBlank()) {
            throw ApiException.validation(
                    "Say why this claim is being rejected. The customer is told the reason.");
        }
        return payIns.toAdminResponse(payIns.reject(claimId, actorOf(actor), request.note()));
    }

    // ── Money that arrived without an owner ────────────────────────────────

    @GetMapping("/unmatched")
    @Operation(summary = "Credits that could not be tied to a customer")
    public PageResponse<PayInDtos.UnmatchedResponse> unmatched(
            @RequestParam(required = false) UnmatchedStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        access.requireCanView();
        return PageResponse.of(
                payIns.unmatchedList(status, PageRequest.of(page, Math.min(size, 100))),
                AdminPayInController::describe);
    }

    /**
     * Records a credit off the statement that no claim matches.
     *
     * <p>Recording it starts the thirty-day clock the Terms commit to, so this
     * is the step that turns "money we cannot place" into an obligation with a
     * deadline rather than a balance that sits there.
     */
    @PostMapping("/unmatched")
    @Operation(summary = "Record an untraceable credit and start the thirty-day hold")
    public PayInDtos.UnmatchedResponse recordUnmatched(
            @Valid @RequestBody PayInDtos.RecordUnmatchedRequest request) {

        AdminUser actor = access.requireCanApprovePayments();
        return describe(payIns.recordUnmatched(request, actorOf(actor)));
    }

    @PostMapping("/unmatched/{unmatchedId}/match")
    @Operation(summary = "Tie a held credit to a customer and credit them")
    public PayInDtos.UnmatchedResponse match(
            @PathVariable UUID unmatchedId,
            @RequestParam UUID customerId,
            @RequestBody(required = false) PayInDtos.ReviewRequest request) {

        AdminUser actor = access.requireCanApprovePayments();
        return describe(payIns.matchToCustomer(
                unmatchedId, customerId, actorOf(actor), request == null ? null : request.note()));
    }

    @PostMapping("/unmatched/{unmatchedId}/return")
    @Operation(summary = "Mark a held credit as returned to source")
    public PayInDtos.UnmatchedResponse markReturned(
            @PathVariable UUID unmatchedId,
            @RequestBody(required = false) PayInDtos.ReviewRequest request) {

        AdminUser actor = access.requireCanApprovePayments();
        return describe(payIns.markReturned(
                unmatchedId, actorOf(actor), request == null ? null : request.note()));
    }

    @PostMapping("/unmatched/{unmatchedId}/trace")
    @Operation(summary = "Add a note about tracing a held credit")
    public PayInDtos.UnmatchedResponse addTraceNote(
            @PathVariable UUID unmatchedId,
            @Valid @RequestBody PayInDtos.ReviewRequest request) {

        AdminUser actor = access.requireCanView();
        return describe(payIns.addTraceNote(unmatchedId, actorOf(actor), request.note()));
    }

    private static PayInDtos.UnmatchedResponse describe(UnmatchedPayIn record) {
        Instant now = Instant.now();
        return new PayInDtos.UnmatchedResponse(
                record.getId(),
                record.getAmount(),
                record.getNarration(),
                record.getSenderName(),
                record.getSenderBank(),
                record.getSenderAccount(),
                record.getBankReference(),
                record.getReceivedAt(),
                record.getStatus().name(),
                record.getStatus().label(),
                record.getReturnDueAt(),
                record.getReturnDueAt() == null ? 0 : ChronoUnit.DAYS.between(now, record.getReturnDueAt()),
                record.getMatchedUserId(),
                record.getTraceNotes());
    }

    private static AuditService.Actor actorOf(AdminUser admin) {
        return new AuditService.Actor(admin.getUserId(), admin.getName(), admin.getEmail());
    }
}
