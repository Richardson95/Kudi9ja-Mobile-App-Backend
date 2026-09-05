package com.quadrilateral.kudi9ja.web.controller;

import com.quadrilateral.kudi9ja.common.idempotency.IdempotencyService;
import com.quadrilateral.kudi9ja.domain.savings.AutoFrequency;
import com.quadrilateral.kudi9ja.domain.savings.SavingsPlan;
import com.quadrilateral.kudi9ja.domain.savings.SavingsService;
import com.quadrilateral.kudi9ja.security.auth.CurrentUser;
import com.quadrilateral.kudi9ja.web.dto.SavingsDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Savings, in two products that behave differently on purpose.
 *
 * <p><b>Fixed</b> is priced by the day — 30 to 1,825 of them, nothing rounded
 * to whole months — and pays its return <b>into the wallet the moment the plan
 * opens</b>. That is only sound because the principal stays put, which is why a
 * Fixed plan cannot be broken: {@code /break} refuses one and says why rather
 * than quietly doing nothing. The only early release is death or permanent
 * incapacity, which is an admin action taken on evidence.
 *
 * <p><b>Target</b> funds a goal over time and pays its bonus on the final day,
 * on what was actually saved rather than what was intended. Breaking one
 * returns <b>every naira of principal in full</b> — no break fee, no cut of the
 * principal — and forfeits only the bonus, which was the whole incentive.
 *
 * <p>Interest is flat and never compounds, in either product. A running plan
 * keeps the rate it was opened on: a later rate change never rewrites history.
 */
@RestController
@RequestMapping("/api/v1/savings")
@Tag(name = "Savings", description = "Fixed and Target plans")
public class SavingsController {

    private final SavingsService savings;
    private final IdempotencyService idempotency;
    private final CurrentUser currentUser;

    public SavingsController(
            SavingsService savings, IdempotencyService idempotency, CurrentUser currentUser) {
        this.savings = savings;
        this.idempotency = idempotency;
        this.currentUser = currentUser;
    }

    // ── Quotes ─────────────────────────────────────────────────────────────

    /**
     * What a Fixed lock would return.
     *
     * <p>Open to a signed-in customer before they commit to anything, and
     * computed by the same code that will price the plan — so the number on the
     * calculator screen is the number they will get, not an approximation of
     * it.
     */
    @GetMapping("/quote/fixed")
    @Operation(summary = "Interest, maturity date and yield for a Fixed lock")
    public SavingsDtos.FixedQuoteResponse quoteFixed(
            @RequestParam BigDecimal amount,
            @RequestParam int days) {
        return savings.quoteFixed(amount, days);
    }

    @GetMapping("/quote/target")
    @Operation(summary = "Per-deposit amount, bonus and finish date for a Target plan")
    public SavingsDtos.TargetQuoteResponse quoteTarget(
            @RequestParam BigDecimal goal,
            @RequestParam int months,
            @RequestParam AutoFrequency frequency) {
        return savings.quoteTarget(goal, frequency, months);
    }

    // ── Plans ──────────────────────────────────────────────────────────────

    @GetMapping("/plans")
    @Operation(summary = "Every plan on this account")
    public List<SavingsDtos.PlanResponse> plans() {
        Instant now = Instant.now();
        return savings.list(currentUser.requireId()).stream()
                .map(plan -> SavingsDtos.PlanResponse.from(plan, now))
                .toList();
    }

    @GetMapping("/plans/{planId}")
    @Operation(summary = "One plan")
    public SavingsDtos.PlanResponse plan(@PathVariable UUID planId) {
        return SavingsDtos.PlanResponse.from(
                savings.get(currentUser.requireId(), planId), Instant.now());
    }

    @PostMapping("/plans/fixed")
    @Operation(summary = "Open a Fixed plan. The return is paid into the wallet at once.")
    public ResponseEntity<SavingsDtos.PlanResponse> createFixed(
            @Valid @RequestBody SavingsDtos.CreateFixedRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {

        UUID userId = currentUser.requireId();
        IdempotencyService.Result<SavingsDtos.PlanResponse> result = idempotency.execute(
                userId,
                key,
                "savings.createFixed",
                new Object[] {request.title(), request.principal(), request.days()},
                SavingsDtos.PlanResponse.class,
                () -> respond(savings.createFixed(userId, request)));

        return created(result);
    }

    @PostMapping("/plans/target")
    @Operation(summary = "Open a Target plan")
    public ResponseEntity<SavingsDtos.PlanResponse> createTarget(
            @Valid @RequestBody SavingsDtos.CreateTargetRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {

        UUID userId = currentUser.requireId();
        IdempotencyService.Result<SavingsDtos.PlanResponse> result = idempotency.execute(
                userId,
                key,
                "savings.createTarget",
                new Object[] {request.title(), request.goal(), request.months(), request.frequency()},
                SavingsDtos.PlanResponse.class,
                () -> respond(savings.createTarget(userId, request)));

        return created(result);
    }

    /**
     * Adds to a plan.
     *
     * <p>On a Fixed plan the top-up earns the return for <b>the days
     * remaining</b>, not the plan's original term, and that interest is paid
     * upfront like the original — the same deal, priced honestly for the time
     * the money will actually be locked.
     */
    @PostMapping("/plans/{planId}/topup")
    @Operation(summary = "Add to a plan")
    public SavingsDtos.PlanResponse topUp(
            @PathVariable UUID planId,
            @Valid @RequestBody SavingsDtos.TopUpRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {

        UUID userId = currentUser.requireId();
        return idempotency.execute(
                userId,
                key,
                "savings.topUp",
                new Object[] {planId, request.amount()},
                SavingsDtos.PlanResponse.class,
                () -> respond(savings.topUp(userId, planId, request.amount(), request.pin())))
                .value();
    }

    @PostMapping("/plans/{planId}/withdraw")
    @Operation(summary = "Release a matured plan into the wallet")
    public SavingsDtos.ReleaseResponse withdraw(
            @PathVariable UUID planId,
            @Valid @RequestBody SavingsDtos.PinOnlyRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {

        UUID userId = currentUser.requireId();
        return idempotency.execute(
                userId,
                key,
                "savings.withdraw",
                planId,
                SavingsDtos.ReleaseResponse.class,
                () -> savings.withdraw(userId, planId, request.pin()))
                .value();
    }

    /**
     * Breaks a Target plan early.
     *
     * <p>Refused on a Fixed plan, with the reason — the return was already paid
     * out on day one, which is only possible because the principal stays put.
     */
    @PostMapping("/plans/{planId}/break")
    @Operation(summary = "Break a Target plan early. Principal returns in full; the bonus is forfeited.")
    public SavingsDtos.ReleaseResponse breakPlan(
            @PathVariable UUID planId,
            @Valid @RequestBody SavingsDtos.PinOnlyRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {

        UUID userId = currentUser.requireId();
        return idempotency.execute(
                userId,
                key,
                "savings.break",
                planId,
                SavingsDtos.ReleaseResponse.class,
                () -> savings.breakPlan(userId, planId, request.pin()))
                .value();
    }

    @PatchMapping("/plans/{planId}/autosave")
    @Operation(summary = "Turn a Target plan's auto-save on or off, or change what it moves")
    public SavingsDtos.PlanResponse updateAutoSave(
            @PathVariable UUID planId,
            @Valid @RequestBody SavingsDtos.AutoSaveRequest request) {

        return respond(savings.updateAutoSave(
                currentUser.requireId(), planId, request.enabled(), request.amount(), request.frequency()));
    }

    private static SavingsDtos.PlanResponse respond(SavingsPlan plan) {
        return SavingsDtos.PlanResponse.from(plan, Instant.now());
    }

    private static ResponseEntity<SavingsDtos.PlanResponse> created(
            IdempotencyService.Result<SavingsDtos.PlanResponse> result) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.value());
    }
}
