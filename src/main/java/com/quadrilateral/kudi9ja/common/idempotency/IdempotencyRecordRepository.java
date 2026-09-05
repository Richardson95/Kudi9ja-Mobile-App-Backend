package com.quadrilateral.kudi9ja.common.idempotency;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    /** Keys older than the retry window a phone could plausibly use. */
    @Modifying
    @Query("delete from IdempotencyRecord r where r.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
