package com.quadrilateral.kudi9ja.domain.thrift;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ThriftContributionRepository extends JpaRepository<ThriftContribution, UUID> {

    boolean existsByCircleIdAndUserIdAndRound(UUID circleId, UUID userId, int round);

    List<ThriftContribution> findByCircleIdAndRoundOrderByPaidAtAsc(UUID circleId, int round);

    List<ThriftContribution> findByCircleIdOrderByRoundAscPaidAtAsc(UUID circleId);

    long countByCircleIdAndRound(UUID circleId, int round);

    List<ThriftContribution> findByCircleIdAndUserIdOrderByRoundAsc(UUID circleId, UUID userId);

    /** What this customer has put into circles that have not finished. */
    @Query("""
            select coalesce(sum(t.amount), 0)
              from ThriftContribution t
             where t.userId = :userId
               and t.circleId in (
                   select c.id from ThriftCircle c where c.completedAt is null)
            """)
    BigDecimal committedToOpenCircles(@Param("userId") UUID userId);
}
