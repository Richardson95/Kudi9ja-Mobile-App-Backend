package com.quadrilateral.kudi9ja.integration.storage;

import com.quadrilateral.kudi9ja.common.error.ApiException;
import com.quadrilateral.kudi9ja.common.error.ErrorCode;
import com.quadrilateral.kudi9ja.config.Kudi9jaProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Receipts on the local filesystem, for a single-node deployment and for tests.
 *
 * <p>A clustered deployment should bind {@link ReceiptStorage} to object
 * storage instead. The signing here is the same shape either way: a key, an
 * expiry, and an HMAC over both, so a URL cannot be edited to reach another
 * customer's receipt or to outlive its window.
 *
 * <p>Files are written under a directory the web server does not serve. Nothing
 * reaches a browser except through the signed-URL endpoint, which checks the
 * signature, checks that the caller is an admin, and writes an audit entry.
 */
@Component
@ConditionalOnProperty(
        name = "kudi9ja.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalReceiptStorage implements ReceiptStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalReceiptStorage.class);

    private final Path root;
    private final ReceiptUrlSigner urlSigner;

    public LocalReceiptStorage(Kudi9jaProperties properties, ReceiptUrlSigner urlSigner) {
        this.root = Path.of(properties.storage().receiptDirectory()).toAbsolutePath().normalize();
        this.urlSigner = urlSigner;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create the receipt directory at " + root, e);
        }
        log.info("Receipts are stored under {}", root);
    }

    @Override
    public String store(String ownerRef, String originalFilename, String contentType, byte[] content) {
        String extension = extensionFor(contentType, originalFilename);
        // The key carries the owner so a stray file can be traced, and a random
        // component so it cannot be guessed from the claim it belongs to.
        String key = sanitise(ownerRef) + "/" + UUID.randomUUID() + extension;
        Path target = resolve(key);

        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL, "That receipt could not be saved. Try again.");
        }
        return key;
    }

    @Override
    public String signedUrl(String key, Duration ttl) {
        return urlSigner.signedUrl(key, ttl);
    }

    @Override
    public InputStream open(String key) {
        Path target = resolve(key);
        if (!Files.exists(target)) {
            throw ApiException.notFound("That receipt");
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL, "That receipt could not be read.");
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("Could not delete the receipt at {}", key, e);
        }
    }

    /**
     * Resolves a key under the root, refusing anything that escapes it.
     *
     * <p>A key arrives from a signed URL, and a signature proves the key has
     * not been edited — but the check costs nothing and a path traversal here
     * would read any file the process can.
     */
    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw ApiException.forbidden("That is not a valid receipt.");
        }
        return target;
    }

    private static String sanitise(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9_-]", "");
    }

    private static String extensionFor(String contentType, String filename) {
        if (contentType != null) {
            switch (contentType.toLowerCase(Locale.ROOT)) {
                case "image/jpeg", "image/jpg" -> {
                    return ".jpg";
                }
                case "image/png" -> {
                    return ".png";
                }
                case "image/heic" -> {
                    return ".heic";
                }
                case "image/webp" -> {
                    return ".webp";
                }
                case "application/pdf" -> {
                    return ".pdf";
                }
                default -> {
                    // fall through to the filename
                }
            }
        }
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot > 0 && dot < filename.length() - 1) {
                String candidate = filename.substring(dot).toLowerCase(Locale.ROOT);
                if (candidate.matches("\\.[a-z0-9]{1,5}")) {
                    return candidate;
                }
            }
        }
        return ".bin";
    }
}
