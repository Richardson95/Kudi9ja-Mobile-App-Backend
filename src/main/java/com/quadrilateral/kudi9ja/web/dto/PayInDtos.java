package com.quadrilateral.kudi9ja.web.dto;

import com.quadrilateral.kudi9ja.domain.payin.DepositPurpose;
import com.quadrilateral.kudi9ja.domain.payin.DepositStatus;
import com.quadrilateral.kudi9ja.domain.payin.PayInClaim;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Claiming a bank transfer, and what an admin sees of it. */
public final class PayInDtos {

    private PayInDtos() {
    }

    /**
     * The reference to quote on the transfer, and where to send it.
     *
     * <p>A fresh reference is minted for every payment. Quoting one twice would
     * make two transfers indistinguishable on a statement, which is the one
     * thing the reference exists to prevent.
     */
    public record PaymentInstructionResponse(
            String reference,
            String bank,
            String accountNumber,
            String accountName,
            BigDecimal minimumAmount,
            String note) {
    }

    /**
     * A claim. Sent as multipart, because the receipt is mandatory: a claim
     * cannot be submitted without one.
     */
    public record ClaimRequest(
            BigDecimal amount,
            String reference,
            DepositPurpose purpose,
            UUID loanId,
            String senderName,
            String senderBank) {
    }

    public record ClaimResponse(
            UUID id,
            BigDecimal amount,
            String reference,
            DepositPurpose purpose,
            String purposeLabel,
            UUID loanId,
            String loanPurpose,
            String senderName,
            String senderBank,
            DepositStatus status,
            String statusLabel,
            Instant claimedAt,
            Instant reviewedAt,
            String reviewedBy,
            String note,
            boolean hasReceipt) {

        public static ClaimResponse from(PayInClaim claim) {
            return new ClaimResponse(
                    claim.getId(),
                    claim.getAmount(),
                    claim.getReference(),
                    claim.getPurpose(),
                    claim.getPurpose().label(),
                    claim.getLoanId(),
                    claim.getLoanPurpose(),
                    claim.getSenderName(),
                    claim.getSenderBank(),
                    claim.getStatus(),
                    claim.getStatus().label(),
                    claim.getClaimedAt(),
                    claim.getReviewedAt(),
                    claim.getReviewedBy(),
                    claim.getNote(),
                    claim.getReceiptKey() != null && !claim.getReceiptKey().isBlank());
        }
    }

    /**
     * The admin's view. Adds the customer, the age of the claim, and a signed
     * URL for the receipt that expires.
     */
    public record AdminClaimResponse(
            UUID id,
            UUID userId,
            String customerName,
            String customerRef,
            BigDecimal amount,
            String reference,
            DepositPurpose purpose,
            String purposeLabel,
            UUID loanId,
            String loanPurpose,
            String senderName,
            String senderBank,
            DepositStatus status,
            String statusLabel,
            Instant claimedAt,
            long hoursWaiting,
            Instant reviewedAt,
            String reviewedBy,
            String note,
            String receiptUrl) {
    }

    /**
     * A reference the customer was handed, as the admin sees it.
     *
     * <p>This is what a bank narration is compared against. An admin looking at
     * a receipt that says {@code K9-A1B2C3-7F4K} can see we issued exactly that,
     * to this customer, at this time — and a narration matching nothing we ever
     * issued is itself worth noticing.
     */
    public record IssuedReferenceResponse(
            String reference,
            Instant copiedAt,
            boolean claimed,
            UUID claimId) {

        public static IssuedReferenceResponse from(
                com.quadrilateral.kudi9ja.domain.payin.PaymentReference issued) {
            return new IssuedReferenceResponse(
                    issued.getReference(),
                    issued.getCopiedAt(),
                    issued.isClaimed(),
                    issued.getClaimId());
        }
    }

    /**
     * Confirming or rejecting a claim. A rejection has to say why.
     *
     * <p>Named for the claim rather than just "review" because {@code SignupDtos}
     * has a {@code ReviewRequest} of its own, for accepting the agreements. Two
     * records with one name collide in the OpenAPI document — only one survives,
     * and every endpoint taking the other is then documented with the wrong
     * shape. Anyone generating a client from that spec builds something that
     * cannot work, and finds out at runtime.
     */
    public record ClaimReviewRequest(String note) {
    }

    /** Recording a credit that arrived without a usable narration. */
    public record RecordUnmatchedRequest(
            BigDecimal amount,
            String narration,
            String senderName,
            String senderBank,
            String senderAccount,
            String bankReference,
            Instant receivedAt) {
    }

    public record UnmatchedResponse(
            UUID id,
            BigDecimal amount,
            String narration,
            String senderName,
            String senderBank,
            String senderAccount,
            String bankReference,
            Instant receivedAt,
            String status,
            String statusLabel,
            Instant returnDueAt,
            long daysUntilReturnDue,
            UUID matchedUserId,
            String traceNotes) {
    }
}
