package com.quadrilateral.kudi9ja.web.controller;

import com.quadrilateral.kudi9ja.common.api.PageResponse;
import com.quadrilateral.kudi9ja.common.idempotency.IdempotencyService;
import com.quadrilateral.kudi9ja.domain.payout.WithdrawalService;
import com.quadrilateral.kudi9ja.domain.wallet.LedgerService;
import com.quadrilateral.kudi9ja.security.auth.CurrentUser;
import com.quadrilateral.kudi9ja.web.dto.WalletDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Money out.
 *
 * <p>The wallet is debited <b>at request, not at approval</b>, and a pending
 * transaction is written at the same moment. That is deliberate and it is the
 * whole design: money that has been asked for must not also be spendable while
 * an admin is looking at the request, or the same naira leaves twice.
 *
 * <p>Declining refunds in full. The pending transaction is reversed rather than
 * deleted — the ledger is append-only, so a decline is a state change on the
 * same row plus a refund, and the customer can see what happened rather than
 * finding a gap.
 *
 * <p>There is no destination to choose. Money goes to the payout account
 * already on the customer's own record, resolved and name-matched when it was
 * set, because the Terms promise payouts only to an account in the customer's
 * own name.
 */
@RestController
@RequestMapping("/api/v1/withdrawals")
@Tag(name = "Withdrawals", description = "Moving money out to the customer's own bank account")
public class WithdrawalController {

    private final WithdrawalService withdrawals;
    private final LedgerService ledger;
    private final IdempotencyService idempotency;
    private final CurrentUser currentUser;

    public WithdrawalController(
            WithdrawalService withdrawals,
            LedgerService ledger,
            IdempotencyService idempotency,
            CurrentUser currentUser) {
        this.withdrawals = withdrawals;
        this.ledger = ledger;
        this.idempotency = idempotency;
        this.currentUser = currentUser;
    }

    /**
     * Requests a withdrawal. Debits immediately.
     *
     * <p>Idempotent on the caller's key: a retry on a dropped connection must
     * not take the money twice, and this is the request most likely to be
     * retried, because the balance visibly changes the moment it succeeds.
     */
    @PostMapping
    @Operation(summary = "Request a withdrawal. The wallet is debited at once.")
    public ResponseEntity<WalletDtos.WithdrawalResponse> request(
            @Valid @RequestBody WalletDtos.WithdrawRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        UUID userId = currentUser.requireId();

        IdempotencyService.Result<WalletDtos.WithdrawalResponse> result = idempotency.execute(
                userId,
                idempotencyKey,
                "withdrawal",
                request.amount(),
                WalletDtos.WithdrawalResponse.class,
                () -> withdrawals.request(
                        userId,
                        request.amount(),
                        request.pin(),
                        request.bank(),
                        request.accountNumber()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.value());
    }

    @GetMapping
    @Operation(summary = "This customer's withdrawal requests")
    public PageResponse<WalletDtos.WithdrawalResponse> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        UUID userId = currentUser.requireId();
        java.math.BigDecimal balance = ledger.balanceOf(userId);

        return PageResponse.of(
                withdrawals.listForCustomer(userId, PageRequest.of(page, Math.min(size, 100))),
                request -> WalletDtos.WithdrawalResponse.from(request, balance, null));
    }

    @GetMapping("/{withdrawalId}")
    @Operation(summary = "One withdrawal request")
    public WalletDtos.WithdrawalResponse one(@PathVariable UUID withdrawalId) {
        UUID userId = currentUser.requireId();
        return WalletDtos.WithdrawalResponse.from(
                withdrawals.getForCustomer(userId, withdrawalId),
                ledger.balanceOf(userId),
                null);
    }
}
