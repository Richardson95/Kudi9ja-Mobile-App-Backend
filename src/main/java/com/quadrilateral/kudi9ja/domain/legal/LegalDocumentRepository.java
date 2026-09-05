package com.quadrilateral.kudi9ja.domain.legal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LegalDocumentRepository extends JpaRepository<LegalDocument, UUID> {

    /**
     * The wording that binds today, newest first.
     *
     * <p>A version published with a future effective date is visible in the
     * admin panel and announced to customers, but does not become the operative
     * document until its date — the thirty days' notice a material change
     * requires would mean nothing if publishing were the same as applying.
     */
    @Query("""
            select d from LegalDocument d
             where d.kind = :kind
               and d.effectiveFrom <= :now
             order by d.effectiveFrom desc, d.publishedAt desc
            """)
    List<LegalDocument> inForce(@Param("kind") LegalDocumentKind kind, @Param("now") Instant now);

    Optional<LegalDocument> findByKindAndVersion(LegalDocumentKind kind, String version);

    List<LegalDocument> findByKindOrderByEffectiveFromDesc(LegalDocumentKind kind);

    /** Announced but not yet in force, for the change notice the app shows. */
    @Query("select d from LegalDocument d where d.effectiveFrom > :now order by d.effectiveFrom asc")
    List<LegalDocument> upcoming(@Param("now") Instant now);

    boolean existsByKindAndVersion(LegalDocumentKind kind, String version);
}
