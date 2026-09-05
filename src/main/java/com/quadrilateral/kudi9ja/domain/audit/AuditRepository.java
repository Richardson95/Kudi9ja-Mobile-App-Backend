package com.quadrilateral.kudi9ja.domain.audit;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads and one append. Deliberately not a {@code JpaRepository}: the audit log
 * is append-only, so {@code delete} and {@code saveAll}-style bulk edits are
 * not offered at all rather than merely discouraged.
 */
public interface AuditRepository extends Repository<AuditEntry, UUID> {

    AuditEntry save(AuditEntry entry);

    @Query("""
            select a from AuditEntry a
             where (:category is null or a.category = :category)
               and (:subjectId is null or a.subjectId = :subjectId)
               and (:from is null or a.occurredAt >= :from)
               and (:to is null or a.occurredAt < :to)
               and (:q is null or :q = ''
                    or lower(a.action) like lower(concat('%', :q, '%'))
                    or lower(a.detail) like lower(concat('%', :q, '%'))
                    or lower(a.actor) like lower(concat('%', :q, '%')))
             order by a.occurredAt desc
            """)
    Page<AuditEntry> search(
            @Param("category") AuditCategory category,
            @Param("subjectId") UUID subjectId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("q") String query,
            Pageable pageable);

    long count();
}
