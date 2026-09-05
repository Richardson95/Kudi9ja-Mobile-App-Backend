package com.quadrilateral.kudi9ja.domain.payin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Money that arrived in the collection account and cannot be tied to a
 * customer.
 *
 * <p>It happens: a customer transfers without the narration, or with the wrong
 * one, or somebody pays in by mistake. The Terms commit us to holding it,
 * attempting to trace it, and <b>returning it to source after thirty days</b>
 * if it still cannot be matched. That promise needs a register, or the money
 * sits in the collection account indefinitely and the commitment is words.
 *
 * <p>Nothing here credits a wallet. Matching one of these to a customer creates
 * a confirmed pay-in against that customer, which is what moves money.
 */
@Entity
@Table(
        name = "unmatched_pay_in",
        indexes = {
                @Index(name = "ix_unmatched_status", columnList = "status, received_at"),
                @Index(name = "ix_unmatched_narration", columnList = "narration")
        })
@Getter
@Setter
@NoArgsConstructor
public class UnmatchedPayIn {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    /** What the bank statement said, verbatim. */
    @Column(name = "narration", length = 500, updatable = false)
    private String narration;

    @Column(name = "sender_name", length = 160, updatable = false)
    private String senderName;

    @Column(name = "sender_bank", length = 120, updatable = false)
    private String senderBank;

    @Column(name = "sender_account", length = 32, updatable = false)
    private String senderAccount;

    /** The bank's own reference for the credit. */
    @Column(name = "bank_reference", length = 120, updatable = false)
    private String bankReference;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt = Instant.now();

    @Column(name = "recorded_by", length = 200)
    private String recordedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UnmatchedStatus status = UnmatchedStatus.HELD;

    /** The date the Terms require it to go back if still unmatched. */
    @Column(name = "return_due_at", nullable = false)
    private Instant returnDueAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by", length = 200)
    private String resolvedBy;

    /** The customer, once one is found. */
    @Column(name = "matched_user_id")
    private UUID matchedUserId;

    @Column(name = "matched_claim_id")
    private UUID matchedClaimId;

    /** What was tried, and what happened. */
    @Column(name = "trace_notes", length = 2000)
    private String traceNotes;

    public boolean isHeld() {
        return status == UnmatchedStatus.HELD;
    }

    public boolean isReturnDue(Instant now) {
        return status == UnmatchedStatus.HELD && !returnDueAt.isAfter(now);
    }
}
