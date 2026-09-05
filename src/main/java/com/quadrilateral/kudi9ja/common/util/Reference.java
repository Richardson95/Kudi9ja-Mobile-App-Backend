package com.quadrilateral.kudi9ja.common.util;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

/**
 * The references a customer reads out and an admin matches on a bank
 * statement, and the customer code that identifies an account.
 *
 * <p>Kudi9ja issues no account numbers. What a customer has is a customer
 * reference, {@code K9-A1B2C3}, for matching payments. It is not payable into.
 */
public final class Reference {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Deliberately missing I, O, 0 and 1. A customer reads this off a screen
     * and types it into a bank app, and the characters that look alike in a
     * bank's font are the ones that get mistyped.
     */
    private static final String UNAMBIGUOUS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private Reference() {
    }

    /**
     * The customer reference: {@code K9-} plus the first six hex characters of
     * the account id, uppercased. Stable for the life of the account.
     */
    public static String customerRef(UUID id) {
        String hex = id.toString().replace("-", "");
        return "K9-" + hex.substring(0, 6).toUpperCase(Locale.ROOT);
    }

    /**
     * A reference unique to one payment: {@code K9-A1B2C3-7F4K}.
     *
     * <p>Not one per customer, one per payment. Two transfers of the same
     * amount on the same day are otherwise impossible to tell apart on a
     * statement.
     */
    public static String paymentReference(String customerRef) {
        StringBuilder suffix = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            suffix.append(UNAMBIGUOUS.charAt(RANDOM.nextInt(UNAMBIGUOUS.length())));
        }
        return customerRef + "-" + suffix;
    }

    /**
     * A ledger reference: a prefix, then a base-36 timestamp and base-36
     * noise, uppercased.
     */
    public static String ledger(String prefix) {
        String ts = Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT);
        String noise = padLeft(Integer.toString(RANDOM.nextInt(0xFFFF), 36).toUpperCase(Locale.ROOT), 4);
        return prefix + "-" + ts + noise;
    }

    /** An invite code for a thrift circle. */
    public static String inviteCode() {
        return ledger("AJO");
    }

    /** A case reference a customer can quote back at support. */
    public static String caseRef(String prefix) {
        return ledger(prefix);
    }

    /** A one-time code: six digits, from a source that cannot be predicted. */
    public static String otp() {
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            code.append(RANDOM.nextInt(10));
        }
        return code.toString();
    }

    private static String padLeft(String value, int length) {
        StringBuilder sb = new StringBuilder(value);
        while (sb.length() < length) {
            sb.insert(0, '0');
        }
        return sb.toString();
    }
}
