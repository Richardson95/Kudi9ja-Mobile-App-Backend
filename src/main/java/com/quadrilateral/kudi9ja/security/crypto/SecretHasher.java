package com.quadrilateral.kudi9ja.security.crypto;

import com.quadrilateral.kudi9ja.config.Kudi9jaProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Every password, passcode, PIN and security answer, one way only.
 *
 * <p>The Flutter client hashes with SHA-256 and a pepper compiled into the
 * repository — a fast hash, and a pepper that is now in public git history. A
 * six-digit passcode under a fast hash is a million guesses, which is seconds
 * of work. Neither is acceptable once the hashes live on a server that can be
 * breached, so this replaces both:
 *
 * <ul>
 *   <li><b>argon2id</b>, memory-hard, with a per-hash random salt, so a stolen
 *       table cannot be brute-forced at any useful rate;
 *   <li>a <b>pepper from secret config</b>, HMAC'd into the value before
 *       hashing, so a database dump alone is not enough — the attacker needs
 *       the application's secrets too;
 *   <li>a <b>constant-time comparison</b>, which argon2's own verify does, so
 *       a timing signal cannot leak a prefix.
 * </ul>
 *
 * <p>The pepper must be rotated as part of the migration away from the client's
 * literal. Rotating it invalidates every stored hash, so a rotation is a
 * deliberate operation with a re-enrolment path, not a config tweak.
 */
@Component
public class SecretHasher {

    private static final String HMAC = "HmacSHA256";

    private final Argon2PasswordEncoder encoder;
    private final byte[] pepper;

    public SecretHasher(Kudi9jaProperties properties) {
        Kudi9jaProperties.Security security = properties.security();
        this.encoder = new Argon2PasswordEncoder(
                security.argonSaltLength(),
                security.argonHashLength(),
                security.argonParallelism(),
                security.argonMemoryKb(),
                security.argonIterations());
        this.pepper = security.pepper().getBytes(StandardCharsets.UTF_8);
    }

    /** Hashes a secret. Never call this on anything that will be returned. */
    public String hash(String raw) {
        return encoder.encode(peppered(raw));
    }

    /**
     * Whether a candidate matches a stored hash.
     *
     * <p>A null or blank stored hash is a miss, not an exception: an account
     * that has not set a PIN yet must fail the check rather than crash on it.
     */
    public boolean matches(String raw, String storedHash) {
        if (raw == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        return encoder.matches(peppered(raw), storedHash);
    }

    /**
     * Whether a stored hash should be rewritten at the next successful use,
     * which is how a cost-parameter increase reaches existing accounts without
     * a mass re-enrolment.
     */
    public boolean needsRehash(String storedHash) {
        return storedHash != null && encoder.upgradeEncoding(storedHash);
    }

    /**
     * A stable, non-reversible token for a value that has to be <i>looked up</i>
     * rather than verified — a BVN checked for reuse across accounts, say.
     * Deterministic by necessity, which is why it is never used for a secret
     * anyone could guess from a small space.
     */
    public String blindIndex(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(pepper, HMAC));
            byte[] digest = mac.doFinal(value.trim().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Could not compute a blind index", e);
        }
    }

    /** Constant-time equality, for comparing two tokens we both hold. */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private String peppered(String raw) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(pepper, HMAC));
            byte[] digest = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Could not apply the pepper", e);
        }
    }
}
