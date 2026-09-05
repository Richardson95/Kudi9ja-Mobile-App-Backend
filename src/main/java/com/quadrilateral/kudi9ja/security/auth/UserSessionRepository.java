package com.quadrilateral.kudi9ja.security.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    List<UserSession> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(UUID userId);

    /**
     * Every session ever opened on this account, revoked ones included.
     *
     * <p>For the data export, where the point is the opposite of the security
     * screen's: not "where am I signed in now" but "where has this account been
     * signed in from", which is the question somebody asks when they think it
     * has been used without them.
     */
    List<UserSession> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Ends every live session for an account at once. Used on a password
     * change, on a freeze, and when a rotated refresh token is presented twice.
     */
    @Modifying
    @Query("""
            update UserSession s
               set s.revokedAt = :now, s.revokedReason = :reason, s.refreshTokenId = null
             where s.userId = :userId
               and s.revokedAt is null
            """)
    int revokeAllForUser(
            @Param("userId") UUID userId,
            @Param("reason") String reason,
            @Param("now") Instant now);

    /** Housekeeping: sessions whose refresh window closed long ago. */
    @Modifying
    @Query("delete from UserSession s where s.refreshExpiresAt is not null and s.refreshExpiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
