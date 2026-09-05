package com.quadrilateral.kudi9ja.domain.legal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalAcceptanceRepository extends JpaRepository<LegalAcceptance, UUID> {

    List<LegalAcceptance> findByUserIdOrderByAcceptedAtDesc(UUID userId);

    Optional<LegalAcceptance> findFirstByUserIdAndKindOrderByAcceptedAtDesc(
            UUID userId, LegalDocumentKind kind);

    boolean existsByUserIdAndKindAndDocumentVersion(
            UUID userId, LegalDocumentKind kind, String documentVersion);

    long countByDocumentId(UUID documentId);
}
