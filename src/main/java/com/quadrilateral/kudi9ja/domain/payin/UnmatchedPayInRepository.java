package com.quadrilateral.kudi9ja.domain.payin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnmatchedPayInRepository extends JpaRepository<UnmatchedPayIn, UUID> {

    Page<UnmatchedPayIn> findByStatusOrderByReceivedAtAsc(
            UnmatchedStatus status, Pageable pageable);

    Page<UnmatchedPayIn> findAllByOrderByReceivedAtDesc(Pageable pageable);

    boolean existsByBankReference(String bankReference);

    /** The daily job: held credits whose thirty days are up. */
    @Query("""
            select u from UnmatchedPayIn u
             where u.status = com.quadrilateral.kudi9ja.domain.payin.UnmatchedStatus.HELD
               and u.returnDueAt <= :now
             order by u.receivedAt asc
            """)
    List<UnmatchedPayIn> dueForReturn(@Param("now") Instant now);

    long countByStatus(UnmatchedStatus status);

    @Query("""
            select coalesce(sum(u.amount), 0)
              from UnmatchedPayIn u
             where u.status = com.quadrilateral.kudi9ja.domain.payin.UnmatchedStatus.HELD
            """)
    BigDecimal heldValue();
}
