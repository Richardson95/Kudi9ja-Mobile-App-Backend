package com.quadrilateral.kudi9ja.domain.wallet;

import com.quadrilateral.kudi9ja.common.error.ApiException;
import com.quadrilateral.kudi9ja.common.util.Money;
import com.quadrilateral.kudi9ja.domain.loan.LoanService;
import com.quadrilateral.kudi9ja.domain.payout.WithdrawalService;
import com.quadrilateral.kudi9ja.domain.savings.SavingsService;
import com.quadrilateral.kudi9ja.domain.settings.PlatformSettings;
import com.quadrilateral.kudi9ja.domain.settings.SettingsService;
import com.quadrilateral.kudi9ja.domain.user.User;
import com.quadrilateral.kudi9ja.domain.user.UserRepository;
import com.quadrilateral.kudi9ja.web.dto.WalletDtos;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The wallet as the customer sees it.
 *
 * <p>Read-only. Nothing here moves money: it is credited by a confirmed pay-in,
 * debited by a withdrawal, and moved between the wallet and a plan or a loan by
 * the service that owns each of those. This one only answers questions.
 *
 * <p>There is deliberately no customer-to-customer transfer. Money leaves
 * Kudi9ja by exactly one route — a withdrawal to the customer's own bank
 * account, approved by an admin — and adding a second route into somebody
 * else's hands is the shape every push-payment fraud takes.
 */
@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final LedgerService ledger;
    private final WalletTransactionRepository transactions;
    private final UserRepository users;
    private final SettingsService settings;
    private final SavingsService savings;
    private final LoanService loans;
    private final WithdrawalService withdrawals;

    public WalletService(
            LedgerService ledger,
            WalletTransactionRepository transactions,
            UserRepository users,
            SettingsService settings,
            SavingsService savings,
            LoanService loans,
            WithdrawalService withdrawals) {
        this.ledger = ledger;
        this.transactions = transactions;
        this.users = users;
        this.settings = settings;
        this.savings = savings;
        this.loans = loans;
        this.withdrawals = withdrawals;
    }

    /**
     * The wallet and the figures around it.
     *
     * <p>The NDIC disclosure travels with the balance rather than being left to
     * a screen to remember: this is not a bank account, and nothing may imply
     * it is.
     */
    @Transactional(readOnly = true)
    public WalletDtos.WalletResponse summary(UUID userId) {
        PlatformSettings s = settings.currentReadOnly();
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("That account"));

        BigDecimal balance = ledger.balanceOf(userId);
        BigDecimal totalSaved = savings.totalSaved(userId);
        BigDecimal totalOwed = loans.totalOwed(userId);
        return new WalletDtos.WalletResponse(
                balance,
                user.getCustomerRef(),
                totalSaved,
                totalOwed,
                Money.add(balance, totalSaved),
                Money.of(transactions.totalInterestEarned(userId)),
                Money.of(transactions.totalDeposited(userId)),
                withdrawals.pendingValueFor(userId),
                user.isHideBalance(),
                "Your Kudi9ja wallet is not a bank account and is not NDIC-insured.");
    }

    @Transactional(readOnly = true)
    public Page<WalletTransaction> transactions(UUID userId, TxFilter filter, Pageable pageable) {
        if (filter == null || filter == TxFilter.ALL) {
            return transactions.findByUserIdOrderByOccurredAtDescSequenceDesc(userId, pageable);
        }
        return transactions.findByUserIdAndKindInOrderByOccurredAtDescSequenceDesc(
                userId, filter.kinds(), pageable);
    }

    @Transactional(readOnly = true)
    public WalletTransaction transaction(UUID userId, UUID transactionId) {
        WalletTransaction tx = transactions.findById(transactionId)
                .orElseThrow(() -> ApiException.notFound("That transaction"));
        if (!tx.getUserId().equals(userId)) {
            throw ApiException.notFound("That transaction");
        }
        return tx;
    }

    /**
     * Whether the stored balance still matches the ledger behind it.
     *
     * <p>The balance is the running total of an append-only ledger and must be
     * reconstructible by replaying it. A divergence is a defect rather than a
     * rounding quirk — every row is written at kobo scale — so this reports one
     * and there is deliberately nothing here that corrects a balance by hand.
     */
    @Transactional(readOnly = true)
    public WalletDtos.ReconciliationResponse reconcile(UUID userId) {
        LedgerService.Reconciliation result = ledger.reconcile(userId);
        return new WalletDtos.ReconciliationResponse(
                result.userId(),
                result.storedBalance(),
                result.ledgerBalance(),
                result.difference(),
                result.balanced());
    }
}
