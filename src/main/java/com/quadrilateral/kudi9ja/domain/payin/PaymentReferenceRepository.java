package com.quadrilateral.kudi9ja.domain.payin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentReferenceRepository extends JpaRepository<PaymentReference, UUID> {

    /** The one currently on the customer's screen, if they have one. */
    Optional<PaymentReference> findFirstByUserIdAndCopiedAtIsNull(UUID userId);

    Optional<PaymentReference> findByReference(String reference);

    boolean existsByReference(String reference);

    /**
     * The references this customer has actually taken away, newest first.
     *
     * <p>What an admin compares a bank narration against. Deliberately excludes
     * the uncopied one: nothing has been promised with it.
     */
    List<PaymentReference> findByUserIdAndCopiedAtIsNotNullOrderByCopiedAtDesc(UUID userId);
}
