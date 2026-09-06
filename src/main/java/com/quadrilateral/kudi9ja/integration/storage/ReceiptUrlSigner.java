package com.quadrilateral.kudi9ja.integration.storage;

import com.quadrilateral.kudi9ja.config.Kudi9jaProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Signs the URLs that admins use to open a receipt.
 *
 * <p>This is deliberately <b>ours</b> rather than the storage provider's, and
 * it stays ours whichever backend is bound. A provider's own signed URL would
 * reach the file directly, and that would skip the two checks that actually
 * matter: that the caller still holds panel access at the moment they open it,
 * and that the view is written to the audit log. So every receipt URL points at
 * {@code /api/v1/admin/receipts/...} no matter where the bytes live, and the
 * backend is only ever asked for a stream.
 *
 * <p>The signature is an HMAC over the key <i>and</i> the expiry together.
 * Signing the key alone would produce a URL that never expires; signing them
 * separately would let the two halves of two different URLs be recombined.
 */
@Component
public class ReceiptUrlSigner {

    private static final String HMAC = "HmacSHA256";

    private final byte[] signingKey;

    public ReceiptUrlSigner(Kudi9jaProperties properties) {
        // The same pepper the password hashes use. It is secret configuration
        // that never reaches the database, so a dump of the claims table does
        // not let anyone mint a receipt URL.
        this.signingKey = properties.security().pepper().getBytes(StandardCharsets.UTF_8);
    }

    /** A URL that works for {@code ttl} and then stops. */
    public String signedUrl(String key, Duration ttl) {
        long expiresAt = Instant.now().plus(ttl).getEpochSecond();
        return "/api/v1/admin/receipts/"
                + Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(key.getBytes(StandardCharsets.UTF_8))
                + "?expires=" + expiresAt
                + "&signature=" + sign(key + "|" + expiresAt);
    }

    /**
     * Whether a signature is genuine and still inside its window.
     *
     * <p>Compared in constant time: a byte-by-byte comparison that stops at the
     * first difference lets an attacker recover a valid signature one character
     * at a time.
     */
    public boolean isSignatureValid(String key, long expiresAt, String signature) {
        if (signature == null || Instant.now().getEpochSecond() > expiresAt) {
            return false;
        }
        return MessageDigest.isEqual(
                sign(key + "|" + expiresAt).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(signingKey, HMAC));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign a receipt URL", e);
        }
    }
}
