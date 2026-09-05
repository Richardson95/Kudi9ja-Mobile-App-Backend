package com.quadrilateral.kudi9ja.domain.user;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SignupDraftRepository extends JpaRepository<SignupDraft, UUID> {

    Optional<SignupDraft> findFirstByEmailAndCompletedAtIsNullOrderByCreatedAtDesc(String email);

    /**
     * An abandoned draft holds a BVN and a NIN for somebody who never opened an
     * account, which is exactly what data minimisation forbids keeping.
     */
    @Modifying
    @Query("delete from SignupDraft d where d.expiresAt < :before and d.completedAt is null")
    int deleteExpiredBefore(@Param("before") Instant before);
}
