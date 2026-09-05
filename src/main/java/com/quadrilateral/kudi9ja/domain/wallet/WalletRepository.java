package com.quadrilateral.kudi9ja.domain.wallet;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserId(UUID userId);

    /**
     * Takes the row under a write lock for the rest of the transaction.
     *
     * <p>Every movement goes through this. Two concurrent debits would
     * otherwise both read the same balance, both find it sufficient, and both
     * succeed — which is exactly how a wallet is spent twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.userId = :userId")
    Optional<Wallet> findByUserIdForUpdate(@Param("userId") UUID userId);

    /**
     * Every naira the company is holding on behalf of customers.
     *
     * <p>This is the figure the collection account statement is reconciled
     * against. It is the panel's first number for that reason: a divergence
     * between this and the bank is either a missed pay-in or a defect, and
     * both need finding the same day.
     */
    @Query("select coalesce(sum(w.balance), 0) from Wallet w")
    java.math.BigDecimal totalHeld();
}
