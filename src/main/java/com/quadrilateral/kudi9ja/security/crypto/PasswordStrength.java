package com.quadrilateral.kudi9ja.security.crypto;

import com.quadrilateral.kudi9ja.common.error.ApiException;
import com.quadrilateral.kudi9ja.common.error.ErrorCode;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a password must clear, and what a passcode or PIN must not be.
 *
 * <p>The rules are deliberately about resisting a guess rather than about
 * satisfying a policy: a long passphrase passes, and a short one with a symbol
 * bolted on does not.
 */
public final class PasswordStrength {

    private static final int MIN_LENGTH = 8;

    /**
     * The codes that a large share of people choose when left to themselves.
     * A six-digit passcode has a million combinations, but the top few hundred
     * cover a real fraction of the population — so those are refused outright.
     */
    private static final Set<String> BANNED_CODES = Set.of(
            "000000", "111111", "222222", "333333", "444444", "555555",
            "666666", "777777", "888888", "999999",
            "123456", "654321", "123123", "121212", "112233", "789456",
            "0000", "1111", "2222", "3333", "4444", "5555", "6666", "7777",
            "8888", "9999", "1234", "4321", "1212", "1122", "2580", "0852");

    private static final List<String> BANNED_PASSWORDS = List.of(
            "password", "passw0rd", "qwerty", "letmein", "welcome",
            "kudi9ja", "kudi9ja1", "nigeria", "abc123", "iloveyou");

    private PasswordStrength() {
    }

    /**
     * Refuses a password that would not survive a dictionary attack.
     *
     * @throws ApiException when it is too short, too obvious, or too plain
     */
    public static void requireStrongPassword(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new ApiException(
                    ErrorCode.WEAK_PASSWORD,
                    "Your password needs at least " + MIN_LENGTH + " characters.");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        for (String banned : BANNED_PASSWORDS) {
            if (lower.contains(banned)) {
                throw new ApiException(
                        ErrorCode.WEAK_PASSWORD,
                        "That password is too easy to guess. Choose something else.");
            }
        }

        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSymbol = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
        boolean longEnoughToRelax = password.length() >= 16;

        // A long passphrase is stronger than a short string with a symbol in
        // it, so length buys its way past the character-class rule.
        if (!longEnoughToRelax && !(hasLetter && (hasDigit || hasSymbol))) {
            throw new ApiException(
                    ErrorCode.WEAK_PASSWORD,
                    "Mix letters with a number or a symbol, or use at least 16 characters.");
        }
    }

    /** The sign-in passcode: six digits, and not one of the obvious ones. */
    public static void requireValidPasscode(String passcode) {
        requireCode(passcode, 6, "passcode");
    }

    /** The transaction PIN: four digits, and not one of the obvious ones. */
    public static void requireValidPin(String pin) {
        requireCode(pin, 4, "PIN");
    }

    private static void requireCode(String code, int length, String what) {
        if (code == null || code.length() != length || !code.chars().allMatch(Character::isDigit)) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "Your " + what + " must be " + length + " digits.");
        }
        if (BANNED_CODES.contains(code)) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "That " + what + " is too easy to guess. Choose another.");
        }
        if (isSequential(code)) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "A run of consecutive digits is too easy to guess. Choose another " + what + ".");
        }
    }

    /** 1234 and 8765 both count: a run in either direction. */
    private static boolean isSequential(String code) {
        boolean ascending = true;
        boolean descending = true;
        for (int i = 1; i < code.length(); i++) {
            int step = code.charAt(i) - code.charAt(i - 1);
            if (step != 1) {
                ascending = false;
            }
            if (step != -1) {
                descending = false;
            }
        }
        return ascending || descending;
    }
}
