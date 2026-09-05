package com.quadrilateral.kudi9ja.common.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A money-moving request that has already been carried out.
 *
 * <p>Every endpoint that moves money takes an idempotency key. A phone on a bad
 * connection retries; without this, a retried pay-in claim or withdrawal would
 * double. With it, the second attempt is answered with the first attempt's
 * result and nothing moves twice.
 *
 * <p>The request fingerprint is stored alongside the key, so reusing a key for
 * a <i>different</i> request is refused rather than silently answered with the
 * wrong result — which would be worse than doubling.
 */
@Entity
@Table(
        name = "idempotency_record",
        indexes = {
                @Index(name = "ix_idem_key", columnList = "user_id, idempotency_key", unique = true),
                @Index(name = "ix_idem_created", columnList = "created_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "idempotency_key", nullable = false, length = 120, updatable = false)
    private String idempotencyKey;

    /** The endpoint, so the same key on two different operations is caught. */
    @Column(name = "operation", nullable = false, length = 120, updatable = false)
    private String operation;

    /** A hash of the request body: the same key must mean the same request. */
    @Column(name = "request_fingerprint", nullable = false, length = 128, updatable = false)
    private String requestFingerprint;

    /** The serialised response the first attempt produced. */
    @Column(name = "response_body", length = 8000)
    private String responseBody;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static IdempotencyRecord of(
            UUID userId, String key, String operation, String fingerprint) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.id = UUID.randomUUID();
        record.userId = userId;
        record.idempotencyKey = key;
        record.operation = operation;
        record.requestFingerprint = fingerprint;
        record.responseStatus = 200;
        return record;
    }
}
