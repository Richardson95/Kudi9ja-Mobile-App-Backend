package com.quadrilateral.kudi9ja.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quadrilateral.kudi9ja.common.error.ApiException;
import com.quadrilateral.kudi9ja.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Runs a money-moving operation at most once per idempotency key.
 *
 * <p>The order matters. The key is claimed in a committed transaction of its
 * own before the work starts, so a second request arriving while the first is
 * still running collides on the unique index rather than running the work
 * again. The result is then written back against that claim.
 *
 * <p>Three outcomes:
 *
 * <ul>
 *   <li><b>New key</b> — the work runs and its result is recorded.
 *   <li><b>Same key, same request</b> — the recorded result is replayed and
 *       nothing moves.
 *   <li><b>Same key, different request</b> — refused. Answering with the first
 *       result would be worse than doubling, because the caller would believe
 *       something happened that did not.
 * </ul>
 */
@Service
public class IdempotencyService {

    private final IdempotencyStore store;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    /**
     * @param key  the client's key. Null or blank runs the work unguarded,
     *             which is the right treatment for a caller who has opted out
     *             rather than a reason to refuse them.
     * @param work the operation, run at most once
     * @param type what {@code work} returns, so a replay can be deserialised
     */
    public <T> Result<T> execute(
            UUID userId,
            String key,
            String operation,
            Object request,
            Class<T> type,
            Supplier<T> work) {

        if (key == null || key.isBlank()) {
            return new Result<>(work.get(), false);
        }

        String fingerprint = fingerprint(operation, request);

        Optional<IdempotencyRecord> existing = store.find(userId, key);
        if (existing.isPresent()) {
            return new Result<>(replay(existing.get(), key, fingerprint, type), true);
        }

        IdempotencyRecord claim;
        try {
            claim = store.claim(userId, key, operation, fingerprint);
        } catch (DataIntegrityViolationException race) {
            // Another request claimed the key between the read and the write.
            IdempotencyRecord winner = store.find(userId, key)
                    .orElseThrow(() -> new ApiException(
                            ErrorCode.IDEMPOTENCY_CONFLICT,
                            "That request is already being processed. Try again in a moment."));
            return new Result<>(replay(winner, key, fingerprint, type), true);
        }

        T result = work.get();
        store.recordResult(claim.getId(), result);
        return new Result<>(result, false);
    }

    /** Housekeeping. A key is only useful for as long as a client might retry. */
    public int purgeOlderThan(Instant before) {
        return store.purgeOlderThan(before);
    }

    private <T> T replay(IdempotencyRecord record, String key, String fingerprint, Class<T> type) {
        if (!MessageDigest.isEqual(
                record.getRequestFingerprint().getBytes(StandardCharsets.UTF_8),
                fingerprint.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(
                    ErrorCode.IDEMPOTENCY_CONFLICT,
                    "That key was already used for a different request. Use a new one.",
                    Map.of("idempotencyKey", key));
        }
        if (record.getResponseBody() == null) {
            throw new ApiException(
                    ErrorCode.IDEMPOTENCY_CONFLICT,
                    "That request has already gone through. Check your history rather than sending it again.",
                    Map.of("idempotencyKey", key));
        }
        try {
            return objectMapper.readValue(record.getResponseBody(), type);
        } catch (Exception e) {
            throw new ApiException(
                    ErrorCode.IDEMPOTENCY_CONFLICT,
                    "That request has already gone through. Check your history rather than sending it again.",
                    Map.of("idempotencyKey", key));
        }
    }

    private String fingerprint(String operation, Object request) {
        try {
            String payload = operation + "|" + objectMapper.writeValueAsString(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of()
                    .formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not fingerprint the request", e);
        }
    }

    /**
     * @param replayed whether this came from a previous attempt rather than
     *                 from running the work now. Surfaced to the client as a
     *                 header, so a retry can be told apart from a fresh call.
     */
    public record Result<T>(T value, boolean replayed) {
    }
}
