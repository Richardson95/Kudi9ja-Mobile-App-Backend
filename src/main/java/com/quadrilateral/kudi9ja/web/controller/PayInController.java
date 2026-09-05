package com.quadrilateral.kudi9ja.web.controller;

import com.quadrilateral.kudi9ja.common.api.PageResponse;
import com.quadrilateral.kudi9ja.common.error.ApiException;
import com.quadrilateral.kudi9ja.common.error.ErrorCode;
import com.quadrilateral.kudi9ja.domain.payin.DepositPurpose;
import com.quadrilateral.kudi9ja.domain.payin.PayInService;
import com.quadrilateral.kudi9ja.security.auth.CurrentUser;
import com.quadrilateral.kudi9ja.web.dto.PayInDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Money in — the only route it takes.
 *
 * <p>There is no card, no USSD and no instant credit. A customer transfers to
 * the collection account from their own bank, quoting a reference we mint, and
 * then claims that payment in the app with a receipt attached. <b>Nothing is
 * credited until an admin has matched the claim against the bank
 * statement.</b> Submitting a claim moves no money and leaves the balance
 * exactly where it was.
 *
 * <p>Two details carry more weight than they look like they should:
 *
 * <ul>
 *   <li><b>Every payment gets its own reference</b>, not one per customer. Two
 *       transfers of the same amount on the same day are otherwise
 *       indistinguishable on a statement, which is the single thing the
 *       reference exists to prevent.
 *   <li><b>The receipt is mandatory.</b> It is what an admin looks at when the
 *       narration is wrong or missing, and it is kept for five years as part of
 *       the transaction record.
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/payins")
@Tag(name = "Pay-ins", description = "Claiming a bank transfer into the wallet")
public class PayInController {

    private final PayInService payIns;
    private final CurrentUser currentUser;

    public PayInController(PayInService payIns, CurrentUser currentUser) {
        this.payIns = payIns;
        this.currentUser = currentUser;
    }

    /**
     * Mints the reference for one payment and returns where to send it.
     *
     * <p>The account named here is the <b>company's collection account</b>, and
     * it is the same for everyone. It is not the customer's account, because
     * Kudi9ja issues none — which is exactly why the reference matters.
     */
    @GetMapping("/reference")
    @Operation(summary = "The reference currently on this customer's screen")
    public PayInDtos.PaymentInstructionResponse reference() {
        return payIns.activeReference(currentUser.requireId());
    }

    /**
     * Called when the customer taps copy.
     *
     * <p>The copy is the event worth recording. Until then the reference is
     * text on a screen; afterwards it is on its way into a bank narration, and
     * an admin holding a statement needs to be able to find it — which is why
     * it is written down here and shown on the customer's admin record.
     *
     * <p>Answers with the <b>next</b> reference, so the screen refreshes the
     * moment the old one reaches the clipboard and the customer cannot reuse
     * one reference for two payments by accident.
     */
    @PostMapping("/reference/copied")
    @Operation(summary = "Record that the reference was copied, and mint the next one")
    public PayInDtos.PaymentInstructionResponse referenceCopied() {
        return payIns.markCopiedAndMintNext(currentUser.requireId());
    }

    /** Kept so an older build of the app still works. */
    @PostMapping("/reference")
    @Operation(summary = "Deprecated — use GET /reference, then POST /reference/copied")
    @Deprecated
    public PayInDtos.PaymentInstructionResponse mintReference() {
        return payIns.activeReference(currentUser.requireId());
    }

    /**
     * Submits a claim, with the receipt.
     *
     * <p>Multipart rather than JSON because the receipt is part of the claim
     * rather than an attachment to it: a claim without one cannot be submitted,
     * so there is no useful request that omits the file.
     *
     * <p>When the purpose is a loan repayment, confirming the claim later
     * credits the wallet <b>and</b> applies the amount to the named loan, so
     * both legs appear in the ledger rather than one arriving from nowhere.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Submit a claim for a transfer already sent, with a receipt")
    public ResponseEntity<PayInDtos.ClaimResponse> submitClaim(
            @RequestParam BigDecimal amount,
            @RequestParam String reference,
            @RequestParam(defaultValue = "WALLET") DepositPurpose purpose,
            @RequestParam(required = false) UUID loanId,
            @RequestParam String senderName,
            @RequestParam(required = false) String senderBank,
            @RequestParam("receipt") MultipartFile receipt) {

        if (receipt == null || receipt.isEmpty()) {
            throw new ApiException(
                    ErrorCode.RECEIPT_REQUIRED,
                    "Attach the receipt from your bank. We cannot confirm a payment without one.");
        }

        byte[] bytes;
        try {
            bytes = receipt.getBytes();
        } catch (IOException e) {
            throw new ApiException(
                    ErrorCode.RECEIPT_REQUIRED,
                    "We could not read that receipt. Try attaching it again.");
        }

        PayInDtos.ClaimRequest request = new PayInDtos.ClaimRequest(
                amount, reference, purpose, loanId, senderName, senderBank);

        return ResponseEntity.status(HttpStatus.CREATED).body(PayInDtos.ClaimResponse.from(
                payIns.submitClaim(
                        currentUser.requireId(),
                        request,
                        receipt.getOriginalFilename(),
                        receipt.getContentType(),
                        bytes)));
    }

    @GetMapping
    @Operation(summary = "The claims this customer has made")
    public PageResponse<PayInDtos.ClaimResponse> myClaims(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        return PageResponse.of(
                payIns.listForCustomer(currentUser.requireId(), PageRequest.of(page, Math.min(size, 100))),
                PayInDtos.ClaimResponse::from);
    }

    @GetMapping("/{claimId}")
    @Operation(summary = "One of this customer's claims")
    public PayInDtos.ClaimResponse claim(@PathVariable UUID claimId) {
        return PayInDtos.ClaimResponse.from(payIns.getForCustomer(currentUser.requireId(), claimId));
    }
}
