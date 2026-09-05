package com.quadrilateral.kudi9ja.domain.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    /**
     * Matched on the token, not on the customer.
     *
     * <p>A handset that changed hands arrives with a token already on file
     * against its previous owner. Looking it up this way is what lets the row
     * move to whoever signed in last, rather than leaving two accounts pointing
     * at one phone.
     */
    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByUserId(UUID userId);

    void deleteByToken(String token);

    int deleteByUserId(UUID userId);

    /** Handsets nobody has opened in a long time. Swept nightly. */
    @Modifying
    @Query("delete from DeviceToken d where d.lastSeenAt < :before")
    int deleteStaleBefore(@Param("before") Instant before);
}
