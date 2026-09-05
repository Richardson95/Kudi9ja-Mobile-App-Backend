package com.quadrilateral.kudi9ja.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two writes that have to commit independently of the work they guard.
 *
 * <p>They live on their own bean rather than on {@link IdempotencyService}
 * because a {@code REQUIRES_NEW} method called from inside the same class goes
 * through {@code this} and not through the proxy, so the new transaction would
 * never actually start — and the claim would not be visible to a concurrent
 * request, which is the entire point of making it.
 */
@Service
public class IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyStore.class);
    private static final int MAX_STORED_RESPONSE = 8000;

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyStore(IdempotencyRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<IdempotencyRecord> find(UUID userId, String key) {
        return repository.findByUserIdAndIdempotencyKey(userId, key);
    }

    /**
     * Claims the key and commits, so a second request racing the first collides
     * on the unique index instead of running the work again.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyRecord claim(UUID userId, String key, String operation, String fingerprint) {
        return repository.saveAndFlush(IdempotencyRecord.of(userId, key, operation, fingerprint));
    }

    /**
     * Writes the result back against the claim.
     *
     * <p>Failing here must not fail the operation: the money has already moved
     * and been committed. A missing body only costs the caller a replay, and
     * the refusal it gets then is honest about that.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordResult(UUID recordId, Object result) {
        try {
            repository.findById(recordId).ifPresent(record -> {
                try {
                    String body = objectMapper.writeValueAsString(result);
                    record.setResponseBody(body.length() > MAX_STORED_RESPONSE ? null : body);
                    record.setResponseStatus(200);
                    repository.save(record);
                } catch (Exception e) {
                    log.warn("Could not serialise the result for idempotency key {}",
                            record.getIdempotencyKey(), e);
                }
            });
        } catch (Exception e) {
            log.warn("Could not record an idempotency result for {}", recordId, e);
        }
    }

    @Transactional
    public int purgeOlderThan(Instant before) {
        return repository.deleteOlderThan(before);
    }
}
