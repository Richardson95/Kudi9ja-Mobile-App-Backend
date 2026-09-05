package com.quadrilateral.kudi9ja.integration.kyc;

import com.quadrilateral.kudi9ja.integration.bank.BankAccountResolver;
import com.quadrilateral.kudi9ja.integration.bank.BankDirectory;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * A stand-in for the licensed providers, for development and tests.
 *
 * <p>It is <b>not</b> the client's fake. The client's version waits 1.5 seconds
 * and always passes, which means no code path that handles a failure ever runs.
 * This one applies the format rules for real and uses a documented convention
 * so that both outcomes are reachable and both are exercised by tests:
 *
 * <ul>
 *   <li>a number ending in {@code 0} is reported as not found;
 *   <li>a number ending in {@code 9} resolves to a deliberately different
 *       person, so the name-mismatch path is testable;
 *   <li>anything else resolves to the name that was asked about.
 * </ul>
 *
 * <p>It refuses to start unless {@code kudi9ja.kyc.provider} is explicitly set
 * to {@code sandbox}, so a production deployment that has not bound a real
 * provider fails to start rather than quietly verifying nobody.
 */
@Component
@ConditionalOnProperty(name = "kudi9ja.kyc.provider", havingValue = "sandbox")
public class SandboxIdentityVerifier implements IdentityVerifier, BankAccountResolver {

    private static final Logger log = LoggerFactory.getLogger(SandboxIdentityVerifier.class);

    public SandboxIdentityVerifier() {
        log.warn("Identity verification is running in SANDBOX mode. "
                + "No BVN, NIN or bank account is being checked against a real institution. "
                + "Bind a licensed provider before this handles real customers.");
    }

    @Override
    public Verification verifyBvn(String bvn) {
        return verifyNumber(bvn, "BVN");
    }

    @Override
    public Verification verifyNin(String nin) {
        return verifyNumber(nin, "NIN");
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
        if (accountNumber.endsWith("0")) {
            return Resolution.failed("No account was found with that number at that bank.");
        }
        if (accountNumber.endsWith("9")) {
            return new Resolution(true, "SANDBOX DIFFERENT PERSON", bankCode, null);
        }
        // The caller compares this against the customer's own name; the
        // sandbox says "whoever you asked about", so the happy path passes.
        return new Resolution(true, SANDBOX_MATCHES_CALLER, bankCode, null);
    }

    /**
     * A sentinel the caller recognises as "the sandbox agrees". A real provider
     * returns a real name here, and the caller's name match runs unchanged.
     */
    public static final String SANDBOX_MATCHES_CALLER = "__SANDBOX_MATCHES_CALLER__";

    private Verification verifyNumber(String number, String kind) {
        if (number == null || number.length() != 11 || !number.chars().allMatch(Character::isDigit)) {
            return Verification.failed("A " + kind + " is eleven digits.");
        }
        if (number.endsWith("0")) {
            return Verification.failed("That " + kind + " was not found.");
        }
        if (number.endsWith("9")) {
            return new Verification(
                    true, "Sandbox", "Mismatch", null, LocalDate.of(1980, 1, 1), null, null);
        }
        return new Verification(
                true, SANDBOX_MATCHES_CALLER, SANDBOX_MATCHES_CALLER, null, null, null, null);
    }
}
