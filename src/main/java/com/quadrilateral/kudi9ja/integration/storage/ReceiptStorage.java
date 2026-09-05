package com.quadrilateral.kudi9ja.integration.storage;

import java.io.InputStream;
import java.time.Duration;

/**
 * Where a receipt lives once it is uploaded.
 *
 * <p>The client holds a local file path. That is not a receipt — it is a
 * pointer to a file on one phone, useless to the admin who has to match the
 * payment and gone the moment the customer clears their gallery.
 *
 * <p>Stored objects are <b>private</b>. They are served to admins over a signed
 * URL that expires, never from a public path, and every view is written to the
 * audit log. They are kept for five years as part of the transaction record.
 */
public interface ReceiptStorage {

    /**
     * Stores an uploaded receipt.
     *
     * @return the key it was stored under, which is what the claim keeps
     */
    String store(String ownerRef, String originalFilename, String contentType, byte[] content);

    /** A URL that works for {@code ttl} and then stops. */
    String signedUrl(String key, Duration ttl);

    /** Opens the object, for an admin who is entitled to see it. */
    InputStream open(String key);

    boolean exists(String key);

    /**
     * Removes an object. Only for a receipt whose retention period has run out
     * — a claim's receipt is part of the transaction record and is kept for
     * five years after the relationship ends.
     */
    void delete(String key);

    /** What was stored, for the response. */
    record Stored(String key, String contentType, long sizeBytes) {
    }
}
