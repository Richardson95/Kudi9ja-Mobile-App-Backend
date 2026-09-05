package com.quadrilateral.kudi9ja.integration.kyc;

import java.time.LocalDate;

/**
 * Checks a BVN and a NIN against the institutions that issued them.
 *
 * <p>The Flutter client fakes this with a 1.5-second delay that always passes.
 * A real deployment must bind this to a licensed verification provider, and the
 * name and date of birth the provider returns must match what the customer
 * typed — a BVN that belongs to somebody else is exactly what this is for.
 */
public interface IdentityVerifier {

    /** Verifies a Bank Verification Number. */
    Verification verifyBvn(String bvn);

    /** Verifies a National Identification Number. */
    Verification verifyNin(String nin);

    /**
     * What the issuing institution said.
     *
     * @param verified   whether the number exists and is in good standing
     * @param firstName  as held by the institution
     * @param lastName   as held by the institution
     * @param middleName as held by the institution, often absent
     * @param dateOfBirth as held by the institution
     * @param phone      as held by the institution, for support to cross-check
     * @param reason     why it failed, when it did
     */
    record Verification(
            boolean verified,
            String firstName,
            String lastName,
            String middleName,
            LocalDate dateOfBirth,
            String phone,
            String reason) {

        public static Verification failed(String reason) {
            return new Verification(false, null, null, null, null, null, reason);
        }

        public String fullName() {
            StringBuilder name = new StringBuilder();
            if (firstName != null) {
                name.append(firstName);
            }
            if (middleName != null && !middleName.isBlank()) {
                name.append(' ').append(middleName);
            }
            if (lastName != null) {
                name.append(' ').append(lastName);
            }
            return name.toString().trim();
        }
    }
}
