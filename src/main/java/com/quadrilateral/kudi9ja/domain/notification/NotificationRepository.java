package com.quadrilateral.kudi9ja.domain.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdAndClearedAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Everything ever sent to this customer, cleared rows included.
     *
     * <p>For the data export. Clearing hides a notification from the feed; it
     * does not unsay it, and a subject access request that quietly omitted the
     * ones they had dismissed would be answering a different question.
     */
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndReadFalseAndClearedAtIsNull(UUID userId);

    @Modifying
    @Query("update Notification n set n.read = true where n.userId = :userId and n.read = false")
    int markAllRead(@Param("userId") UUID userId);

    /**
     * Clearing hides the list without destroying it. A customer who clears
     * their notifications has not asked us to forget that we told them their
     * withdrawal was declined.
     */
    @Modifying
    @Query("""
            update Notification n
               set n.clearedAt = :now, n.read = true
             where n.userId = :userId
               and n.clearedAt is null
            """)
    int clearAll(@Param("userId") UUID userId, @Param("now") Instant now);
}
