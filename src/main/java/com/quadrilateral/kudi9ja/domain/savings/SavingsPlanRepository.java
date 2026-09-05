package com.quadrilateral.kudi9ja.domain.savings;

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

public interface SavingsPlanRepository extends JpaRepository<SavingsPlan, UUID> {

    List<SavingsPlan> findByUserIdOrderByStartDateDesc(UUID userId);

    Page<SavingsPlan> findByUserIdOrderByStartDateDesc(UUID userId, Pageable pageable);

    Optional<SavingsPlan> findByIdAndUserId(UUID id, UUID userId);

    List<SavingsPlan> findByUserIdAndStatusInOrderByMaturityDateAsc(
            UUID userId, List<SavingsStatus> statuses);

    long countByUserId(UUID userId);

    /**
     * Everything currently locked away for this customer.
     *
     * <p>Only open plans count. A withdrawn plan's principal is back in the
     * wallet, and counting it here would inflate both the credit score and the
     * loan offer with money the customer has already taken out.
     */
    @Query("""
            select coalesce(sum(p.principal), 0)
              from SavingsPlan p
             where p.userId = :userId
               and p.status in (
                   com.quadrilateral.kudi9ja.domain.savings.SavingsStatus.ACTIVE,
                   com.quadrilateral.kudi9ja.domain.savings.SavingsStatus.MATURED)
            """)
    BigDecimal totalSaved(@Param("userId") UUID userId);

    @Query("""
            select coalesce(sum(p.interestPaid), 0)
              from SavingsPlan p
             where p.userId = :userId
            """)
    BigDecimal totalInterestPaid(@Param("userId") UUID userId);

    /** The hourly maturity sweep: active plans past their maturity date. */
    @Query("""
            select p from SavingsPlan p
             where p.status = com.quadrilateral.kudi9ja.domain.savings.SavingsStatus.ACTIVE
               and p.maturityDate <= :now
            """)
    List<SavingsPlan> findMatured(@Param("now") Instant now);

    /** The hourly auto-save run: target contributions that have fallen due. */
    @Query("""
            select p from SavingsPlan p
             where p.status = com.quadrilateral.kudi9ja.domain.savings.SavingsStatus.ACTIVE
               and p.autoEnabled = true
               and p.nextAutoRun is not null
               and p.nextAutoRun <= :now
             order by p.nextAutoRun asc
            """)
    List<SavingsPlan> findDueAutoSaves(@Param("now") Instant now);

    long countByStatus(SavingsStatus status);

    @Query("""
            select coalesce(sum(p.principal), 0)
              from SavingsPlan p
             where p.status in (
                   com.quadrilateral.kudi9ja.domain.savings.SavingsStatus.ACTIVE,
                   com.quadrilateral.kudi9ja.domain.savings.SavingsStatus.MATURED)
            """)
    BigDecimal totalSavedAcrossBook();

    @Query("select coalesce(sum(p.interestPaid), 0) from SavingsPlan p")
    BigDecimal totalInterestPaidAcrossBook();
}
