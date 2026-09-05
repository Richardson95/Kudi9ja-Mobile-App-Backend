package com.quadrilateral.kudi9ja.web.controller;

import com.quadrilateral.kudi9ja.common.api.PageResponse;
import com.quadrilateral.kudi9ja.domain.wallet.TxFilter;
import com.quadrilateral.kudi9ja.domain.wallet.WalletService;
import com.quadrilateral.kudi9ja.security.auth.CurrentUser;
import com.quadrilateral.kudi9ja.web.dto.WalletDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The wallet: what is in it, and what has moved through it.
 *
 * <p>A wallet is a <b>balance plus an append-only ledger</b>, and the balance is
 * the running total of that ledger. Every figure here is read from the server's
 * ledger rather than computed by the app — the client used to add these up on
 * the device, and a balance the client can compute is a balance the client can
 * lie about.
 *
 * <p>The wallet is not a bank account and is not NDIC-insured. The Terms say so
 * in those words, and nothing on this controller implies otherwise: there is no
 * account number to pay into, because <b>Kudi9ja issues none</b>. What a
 * customer has instead is a customer reference, which is for matching a payment
 * and cannot be paid into.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Wallet", description = "Balance and ledger")
public class WalletController {

    private final WalletService wallet;
    private final CurrentUser currentUser;

    public WalletController(WalletService wallet, CurrentUser currentUser) {
        this.wallet = wallet;
        this.currentUser = currentUser;
    }

    @GetMapping("/wallet")
    @Operation(summary = "Balance and the totals derived from the ledger")
    public WalletDtos.WalletResponse wallet() {
        return wallet.summary(currentUser.requireId());
    }

    @GetMapping("/transactions")
    @Operation(summary = "The ledger, filterable")
    public PageResponse<WalletDtos.TransactionResponse> transactions(
            @Parameter(description = "all, deposits, withdrawals, savings, loans or fees")
            @RequestParam(defaultValue = "ALL") TxFilter filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        return PageResponse.of(
                wallet.transactions(currentUser.requireId(), filter, PageRequest.of(page, Math.min(size, 100))),
                WalletDtos.TransactionResponse::from);
    }

    @GetMapping("/transactions/{transactionId}")
    @Operation(summary = "One entry from the ledger")
    public WalletDtos.TransactionResponse transaction(@PathVariable UUID transactionId) {
        return WalletDtos.TransactionResponse.from(
                wallet.transaction(currentUser.requireId(), transactionId));
    }

    /**
     * Whether this wallet still matches its own ledger.
     *
     * <p>The balance must be reconstructible from the ledger, and a divergence
     * is a defect rather than a curiosity. Exposing the check to the customer
     * makes it something they can point at rather than something only we can
     * see.
     */
    @GetMapping("/wallet/reconciliation")
    @Operation(summary = "Check the balance against the ledger")
    public WalletDtos.ReconciliationResponse reconcile() {
        return wallet.reconcile(currentUser.requireId());
    }
}
