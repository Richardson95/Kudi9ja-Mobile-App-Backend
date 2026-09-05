package com.quadrilateral.kudi9ja.domain.payin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A reference the customer was given for one payment.
 *
 * <p>It exists because of what happens between the app and the bank. The
 * customer copies a reference, leaves for their banking app, and transfers with
 * it in the narration. If nothing recorded that they were given it, the only
 * copy would be the one they typed — and an admin holding a bank statement
 * would have nothing to compare it against until a claim arrived, if it ever
 * did.
 *
 * <p>So a reference is written down the moment it is <b>copied</b>, not when it
 * is displayed. Opening the pay-in screen mints nothing: a customer who looks
 * at the screen three times has not made three payments, and three references
 * on their record would be three things for an admin to rule out.
 *
 * <p>One is active at a time. Copying it retires it and mints the next, so
 * every payment carries its own — two transfers of the same amount on the same
 * day are otherwise impossible to tell apart on a statement.
 */
@Entity
@Table(
        name = "payment_reference",
        indexes = {
                @Index(name = "ix_payref_value", columnList = "reference", unique = true),
                @Index(name = "ix_payref_user", columnList = "user_id, issued_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class PaymentReference {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** What the customer quotes as the narration. */
    @Column(name = "reference", nullable = false, length = 64)
    private String reference;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt = Instant.now();

    /**
     * When the customer actually copied it.
     *
     * <p>Null means it is the one currently on their screen and not yet spoken
     * for. An admin never sees these: nothing has been promised with them.
     */
    @Column(name = "copied_at")
    private Instant copiedAt;

    /** Set when a claim quotes this reference, so a used one can be told apart. */
    @Column(name = "claim_id")
    private UUID claimId;

    public static PaymentReference issue(UUID userId, String reference) {
        PaymentReference issued = new PaymentReference();
        issued.id = UUID.randomUUID();
        issued.userId = userId;
        issued.reference = reference;
        return issued;
    }

    /** Whether this is the one currently on the customer's screen. */
    public boolean isActive() {
        return copiedAt == null;
    }

    public boolean isClaimed() {
        return claimId != null;
    }
}
