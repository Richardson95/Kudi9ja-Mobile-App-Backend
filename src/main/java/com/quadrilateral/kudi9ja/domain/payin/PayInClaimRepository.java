package com.quadrilateral.kudi9ja.domain.payin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayInClaimRepository extends JpaRepository<PayInClaim, UUID> {

    Page<PayInClaim> findByUserIdOrderByClaimedAtDesc(UUID userId, Pageable pageable);

    List<PayInClaim> findByUserIdOrderByClaimedAtDesc(UUID userId);

    Optional<PayInClaim> findByIdAndUserId(UUID id, UUID userId);

    Optional<PayInClaim> findByReference(String reference);

    boolean existsByReference(String reference);

    /** The admin queue, oldest first: the one waiting longest is reviewed first. */
    /** The free-text term is cast for Postgres; see {@link com.quadrilateral.kudi9ja.domain.audit.AuditRepository}. */
    @Query("""
            select c from PayInClaim c
             where (:status is null or c.status = :status)
               and (coalesce(cast(:q as String), '') = ''
                    or upper(c.reference) like upper(concat('%', :q, '%'))
                    or lower(c.customerName) like lower(concat('%', :q, '%'))
                    or upper(c.customerRef) like upper(concat('%', :q, '%'))
                    or lower(c.senderName) like lower(concat('%', :q, '%')))
             order by c.claimedAt asc
            """)
    Page<PayInClaim> queue(
            @Param("status") DepositStatus status,
            @Param("q") String query,
            Pageable pageable);

    long countByStatus(DepositStatus status);

    @Query("""
            select coalesce(sum(c.amount), 0)
              from PayInClaim c
             where c.status = com.quadrilateral.kudi9ja.domain.payin.DepositStatus.PENDING
            """)
    BigDecimal pendingValue();

    /**
     * Claims that have sat unreviewed past the working-day promise. The daily
     * job surfaces these rather than letting a customer wait quietly.
     */
    @Query("""
            select c from PayInClaim c
             where c.status = com.quadrilateral.kudi9ja.domain.payin.DepositStatus.PENDING
               and c.claimedAt < :before
             order by c.claimedAt asc
            """)
    List<PayInClaim> pendingSince(@Param("before") Instant before);

    List<PayInClaim> findByUserIdAndStatusOrderByClaimedAtDesc(UUID userId, DepositStatus status);
}
