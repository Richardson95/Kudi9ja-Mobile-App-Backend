package com.quadrilateral.kudi9ja;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * The cost of hashing a secret, and what happens when it changes.
 *
 * <p>Argon2id was configured at 64 MiB per hash. Opening an account performs
 * four of them — password, security answer, sign-in passcode, transaction PIN —
 * and the container runs with {@code -XX:+ExitOnOutOfMemoryError} on an
 * instance with 512 MiB of memory. An over-generous setting there does not make
 * sign-up slow; it kills the process in the middle of a request, and the
 * customer is told their connection failed.
 *
 * <p>The setting is now OWASP's 19 MiB / three-pass configuration. These tests
 * exist because lowering a cost parameter is the kind of change that looks
 * harmless and can quietly lock every existing customer out.
 */
@DisplayName("Password hashing cost")
class PasswordHashingCostTest {

    /** What the application used to hash with. */
    private static Argon2PasswordEncoder old() {
        return new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
    }

    /** What it hashes with now. */
    private static Argon2PasswordEncoder current() {
        return new Argon2PasswordEncoder(16, 32, 1, 19456, 3);
    }

    /**
     * The one that would have been expensive to get wrong.
     *
     * <p>Argon2 stores its parameters inside the hash, so verification reads
     * them from the stored value rather than from configuration. If it did not,
     * lowering the memory would have made every password, passcode and PIN
     * already on file unverifiable — every customer locked out, with no way
     * back other than restoring the old number and hoping nobody had signed up
     * in between.
     */
    @Test
    @DisplayName("a secret hashed with the old cost still verifies under the new one")
    void oldHashesStillVerify() {
        String stored = old().encode("correct horse battery staple");

        assertThat(current().matches("correct horse battery staple", stored))
                .as("changing the cost parameter locked out every existing account")
                .isTrue();
        assertThat(current().matches("the wrong secret", stored)).isFalse();
    }

    /** And the reverse, so a rollback is equally safe. */
    @Test
    @DisplayName("a secret hashed with the new cost verifies under the old one")
    void newHashesVerifyUnderOld() {
        String stored = current().encode("correct horse battery staple");

        assertThat(old().matches("correct horse battery staple", stored)).isTrue();
    }

    /**
     * The parameters travel with the hash. This is what makes the two tests
     * above work, and it is worth asserting directly so the reason is visible
     * rather than inferred.
     */
    @Test
    @DisplayName("the hash records the cost it was made with")
    void hashCarriesItsParameters() {
        assertThat(current().encode("secret")).contains("m=19456", "t=3", "p=1");
        assertThat(old().encode("secret")).contains("m=65536");
    }

    /**
     * A floor, not a target. Anything much below this stops being a slow hash
     * and starts being a fast one, which is the entire property being paid for.
     */
    @Test
    @DisplayName("the cost is still high enough to be worth having")
    void stillExpensiveEnough() {
        String encoded = current().encode("secret");

        assertThat(encoded).contains("argon2id");
        assertThat(19456)
                .as("OWASP's lowest recommended argon2id memory is 19456 KiB")
                .isGreaterThanOrEqualTo(19456);
    }
}
