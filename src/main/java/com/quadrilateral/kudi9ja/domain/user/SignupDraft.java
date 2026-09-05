package com.quadrilateral.kudi9ja.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A signup in progress.
 *
 * <p>The client runs an eight-step wizard and holds the answers as it goes.
 * The server keeps its own copy so that each step can be validated when it is
 * submitted rather than all at once at the end — a customer who mistypes a BVN
 * should hear about it at step three, not after choosing a PIN — and so a
 * customer whose phone dies at step six can resume rather than start again.
 *
 * <p>Credentials are hashed the moment they arrive, so a draft never holds a
 * password, passcode or PIN in the clear even for the minutes it exists.
 *
 * <p>Drafts expire. An abandoned one carries a BVN and a NIN, and holding
 * identity documents for somebody who never opened an account is exactly what
 * data minimisation forbids.
 */
@Entity
@Table(
        name = "signup_draft",
        indexes = {
                @Index(name = "ix_draft_email", columnList = "email"),
                @Index(name = "ix_draft_expires", columnList = "expires_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class SignupDraft {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "step", nullable = false, length = 24)
    private SignupStep step = SignupStep.PERSONAL;

    // Step 1: personal details ----------------------------------------------

    @Column(name = "full_name", length = 160)
    private String fullName;

    @Column(name = "email", nullable = false, length = 190)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 32)
    private String gender;

    // Step 2: email verification --------------------------------------------

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    // Step 3: identity -------------------------------------------------------

    @Column(name = "bvn", length = 11)
    private String bvn;

    @Column(name = "nin", length = 11)
    private String nin;

    @Column(name = "address", length = 400)
    private String address;

    @Column(name = "state", length = 60)
    private String state;

    @Column(name = "identity_verified_at")
    private Instant identityVerifiedAt;

    // Step 4: payout account -------------------------------------------------

    @Column(name = "payout_bank", length = 120)
    private String payoutBank;

    @Column(name = "payout_account_number", length = 10)
    private String payoutAccountNumber;

    /** The name the bank returned. Kept as the evidence of the name match. */
    @Column(name = "payout_account_name", length = 160)
    private String payoutAccountName;

    // Steps 5 to 7: credentials, hashed on arrival ---------------------------

    @Column(name = "password_hash", length = 300)
    private String passwordHash;

    @Column(name = "security_question", length = 200)
    private String securityQuestion;

    @Column(name = "security_answer_hash", length = 300)
    private String securityAnswerHash;

    @Column(name = "passcode_hash", length = 300)
    private String passcodeHash;

    @Column(name = "pin_hash", length = 300)
    private String pinHash;

    // Lifecycle ---------------------------------------------------------------

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Set when the account is created, so a draft cannot be spent twice. */
    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "user_id")
    private UUID userId;

    /** What the customer is signing up on, recorded against their acceptance. */
    @Column(name = "device", length = 300)
    private String device;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    public static SignupDraft start(String email, Instant expiresAt) {
        SignupDraft draft = new SignupDraft();
        draft.id = UUID.randomUUID();
        draft.email = email.trim().toLowerCase(java.util.Locale.ROOT);
        draft.expiresAt = expiresAt;
        return draft;
    }

    public boolean isUsable(Instant now) {
        return completedAt == null && expiresAt.isAfter(now);
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    /** Moves the draft on, never backwards, so re-submitting a step is safe. */
    public void advanceTo(SignupStep next) {
        if (next.ordinal() > step.ordinal()) {
            this.step = next;
        }
        touch();
    }
}
