package com.quadrilateral.kudi9ja.integration.kyc;

import com.quadrilateral.kudi9ja.integration.bank.BankAccountResolver;
import com.quadrilateral.kudi9ja.integration.bank.BankDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Takes the customer's word for it.
 *
 * <p>A deliberate business decision, not an oversight, and named so that nobody
 * reading the code mistakes it for verification. It accepts any well-formed BVN
 * and NIN, and any account number at a known bank, without asking anyone
 * whether they exist or whose they are.
 *
 * <p><b>What this means in practice.</b> Somebody can open a Kudi9ja account
 * using another person's BVN and NIN, and nothing here will notice. The
 * identity on an account is what the customer typed, and should be treated as a
 * claim rather than a fact — by support, by anyone reading the admin panel, and
 * by anyone deciding what a new account may do.
 *
 * <p>It also means the payout account is unverified. The Terms promise payouts
 * only into an account in the customer's own name; with no name enquiry, that
 * promise rests on the customer having typed their own account number.
 *
 * <p>The format checks are kept — eleven digits, a bank on the list, ten digits
 * — because a typo is worth catching even when nothing else is. They are input
 * validation, not verification, and the difference matters.
 *
 * <p>Binding a licensed provider later changes nothing above this class: both
 * interfaces stay as they are, and {@code KycService} already refuses a
 * mismatch when a real provider returns one.
 */
@Component
@ConditionalOnProperty(name = "kudi9ja.kyc.provider", havingValue = "self-declared")
public class SelfDeclaredIdentityVerifier implements IdentityVerifier, BankAccountResolver {

    private static final Logger log = LoggerFactory.getLogger(SelfDeclaredIdentityVerifier.class);

    public SelfDeclaredIdentityVerifier() {
        log.warn("Identity is SELF-DECLARED. No BVN, NIN or bank account is checked against any "
                + "institution — an account can be opened in somebody else's name, and a payout "
                + "account is whatever the customer typed. This is a configured choice; bind a "
                + "licensed provider to change it.");
    }

    @Override
    public Verification verifyBvn(String bvn) {
        return accept(bvn, "BVN");
    }

    @Override
    public Verification verifyNin(String nin) {
        return accept(nin, "NIN");
    }

    /**
     * Accepts the number as given.
     *
     * <p>Answers with the sentinel {@code KycService} reads as "the provider
     * agrees with the name you already have", so the name-match step passes
     * without inventing a name we were never told.
     */
    private Verification accept(String number, String kind) {
        if (number == null || number.length() != 11 || !number.chars().allMatch(Character::isDigit)) {
            return Verification.failed("A " + kind + " is eleven digits.");
        }
        return new Verification(
                true,
                SelfDeclaredIdentityVerifier.MATCHES_CALLER,
                SelfDeclaredIdentityVerifier.MATCHES_CALLER,
                null, null, null, null);
    }

    @Override
    public Resolution resolve(String bankCode, String accountNumber) {
        if (accountNumber == null || accountNumber.length() != 10
                || !accountNumber.chars().allMatch(Character::isDigit)) {
            return Resolution.failed("An account number is ten digits.");
        }
        if (BankDirectory.byCode(bankCode).isEmpty()) {
            return Resolution.failed("We do not recognise that bank.");
        }
        return new Resolution(true, MATCHES_CALLER, bankCode, null);
    }

    /**
     * "Whoever you asked about."
     *
     * <p>The same sentinel the sandbox uses, so {@code KycService} handles both
     * without a second branch. A real provider returns a real name here and the
     * name match runs properly.
     */
    public static final String MATCHES_CALLER = SandboxIdentityVerifier.SANDBOX_MATCHES_CALLER;
}
