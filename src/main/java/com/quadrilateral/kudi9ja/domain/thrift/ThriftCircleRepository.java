package com.quadrilateral.kudi9ja.domain.thrift;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ThriftCircleRepository extends JpaRepository<ThriftCircle, UUID> {

    Optional<ThriftCircle> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    /** Every circle this customer holds a seat in, whether or not they made it. */
    @Query("""
            select distinct c from ThriftCircle c
              join c.members m
             where m.userId = :userId
               and m.leftAt is null
             order by c.createdAt desc
            """)
    List<ThriftCircle> findForMember(@Param("userId") UUID userId);

    @Query("""
            select count(c) from ThriftCircle c
              join c.members m
             where m.userId = :userId
               and m.leftAt is null
               and c.completedAt is null
            """)
    long countActiveForMember(@Param("userId") UUID userId);
}
