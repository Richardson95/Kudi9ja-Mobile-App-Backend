package com.quadrilateral.kudi9ja.domain.payout;

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

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, UUID> {

    Page<WithdrawalRequest> findByUserIdOrderByRequestedAtDesc(UUID userId, Pageable pageable);

    List<WithdrawalRequest> findByUserIdOrderByRequestedAtDesc(UUID userId);

    Optional<WithdrawalRequest> findByIdAndUserId(UUID id, UUID userId);

    /** The admin queue, oldest first. Reviewed within one working day. */
    @Query("""
            select w from WithdrawalRequest w
             where (:status is null or w.status = :status)
               and (:q is null or :q = ''
                    or lower(w.customerName) like lower(concat('%', :q, '%'))
                    or upper(w.customerRef) like upper(concat('%', :q, '%'))
                    or upper(w.reference) like upper(concat('%', :q, '%'))
                    or w.destinationAccount like concat('%', :q, '%'))
             order by w.requestedAt asc
            """)
    Page<WithdrawalRequest> queue(
            @Param("status") WithdrawalStatus status,
            @Param("q") String query,
            Pageable pageable);

    long countByStatus(WithdrawalStatus status);

    @Query("""
            select coalesce(sum(w.amount), 0)
              from WithdrawalRequest w
             where w.status = com.quadrilateral.kudi9ja.domain.payout.WithdrawalStatus.PENDING
            """)
    BigDecimal pendingValue();

    /** Requests still waiting past the working-day promise. */
    @Query("""
            select w from WithdrawalRequest w
             where w.status = com.quadrilateral.kudi9ja.domain.payout.WithdrawalStatus.PENDING
               and w.requestedAt < :before
             order by w.requestedAt asc
            """)
    List<WithdrawalRequest> pendingSince(@Param("before") Instant before);
}
