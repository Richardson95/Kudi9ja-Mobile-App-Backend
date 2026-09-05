package com.quadrilateral.kudi9ja.domain.kyc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OneTimeCodeRepository extends JpaRepository<OneTimeCode, UUID> {

    /** The code currently in play for this address and purpose. */
    Optional<OneTimeCode> findFirstByTargetAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String target, OtpPurpose purpose);

    /** Whether the resend cooldown has elapsed. */
    Optional<OneTimeCode> findFirstByTargetAndPurposeOrderByCreatedAtDesc(String target, OtpPurpose purpose);

    /** Rate limiting: how many have gone to this address in the last hour. */
    long countByTargetAndPurposeAndCreatedAtAfter(String target, OtpPurpose purpose, Instant since);

    /**
     * Retires every outstanding code for an address and purpose. Issuing a new
     * one invalidates the last: two live codes would double the guessing space
     * an attacker gets for free.
     */
    @Modifying
    @Query("""
            update OneTimeCode c
               set c.consumedAt = :now
             where c.target = :target
               and c.purpose = :purpose
               and c.consumedAt is null
            """)
    int consumeOutstanding(
            @Param("target") String target,
            @Param("purpose") OtpPurpose purpose,
            @Param("now") Instant now);

    @Modifying
    @Query("delete from OneTimeCode c where c.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
