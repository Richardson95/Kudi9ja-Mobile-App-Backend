package com.quadrilateral.kudi9ja.domain.payin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A customer telling us they have paid into the collection account, with a
 * receipt as proof.
 *
 * <p><b>Nothing moves on their word.</b> The wallet is credited, or the loan
 * reduced, only once an admin has matched the reference against the bank
 * statement and seen the receipt. Until then the balance is unchanged and this
 * row is the only thing that exists.
 *
 * <p>The reference is unique to <b>this payment</b>, not to the customer. Two
 * transfers of the same amount on the same day are otherwise impossible to tell
 * apart on a statement.
 */
@Entity
@Table(
        name = "pay_in_claim",
        indexes = {
                @Index(name = "ix_payin_user", columnList = "user_id, claimed_at"),
                @Index(name = "ix_payin_status", columnList = "status, claimed_at"),
                @Index(name = "ix_payin_reference", columnList = "reference", unique = true)
        })
@Getter
@Setter
@NoArgsConstructor
public class PayInClaim {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Copied at claim time so the queue reads without a join. */
    @Column(name = "customer_name", nullable = false, length = 160, updatable = false)
    private String customerName;

    @Column(name = "customer_ref", nullable = false, length = 16, updatable = false)
    private String customerRef;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "claimed_at", nullable = false, updatable = false)
    private Instant claimedAt = Instant.now();

    /** The narration the customer was told to quote: {@code K9-A1B2C3-7F4K}. */
    @Column(name = "reference", nullable = false, length = 64, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 24, updatable = false)
    private DepositPurpose purpose = DepositPurpose.WALLET;

    /** Set when the payment is settling a specific loan. */
    @Column(name = "loan_id")
    private UUID loanId;

    @Column(name = "loan_purpose", length = 200)
    private String loanPurpose;

    /**
     * The stored object, not a path on a phone. <b>Required</b>: a claim cannot
     * be submitted without one.
     */
    @Column(name = "receipt_key", nullable = false, length = 300, updatable = false)
    private String receiptKey;

    @Column(name = "receipt_content_type", length = 120, updatable = false)
    private String receiptContentType;

    @Column(name = "receipt_size_bytes", updatable = false)
    private Long receiptSizeBytes;

    /** Whose bank account the money came from, as the customer reported it. */
    @Column(name = "sender_name", length = 160, updatable = false)
    private String senderName;

    @Column(name = "sender_bank", length = 120, updatable = false)
    private String senderBank;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DepositStatus status = DepositStatus.PENDING;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by", length = 200)
    private String reviewedBy;

    /** The reason, when it was rejected. The customer is told it. */
    @Column(name = "note", length = 1000)
    private String note;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    public boolean isPending() {
        return status == DepositStatus.PENDING;
    }

    public boolean isLoanRepayment() {
        return purpose == DepositPurpose.LOAN_REPAYMENT;
    }
}
