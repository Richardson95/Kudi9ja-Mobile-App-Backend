package com.quadrilateral.kudi9ja.domain.savings;

import com.quadrilateral.kudi9ja.common.error.ApiException;
import com.quadrilateral.kudi9ja.common.error.ErrorCode;
import com.quadrilateral.kudi9ja.common.util.Dates;
import com.quadrilateral.kudi9ja.common.util.Money;
import com.quadrilateral.kudi9ja.domain.notification.NotificationService;
import com.quadrilateral.kudi9ja.domain.notification.NotifyKind;
import com.quadrilateral.kudi9ja.domain.settings.PlatformSettings;
import com.quadrilateral.kudi9ja.domain.settings.SettingsService;
import com.quadrilateral.kudi9ja.domain.user.AuthService;
import com.quadrilateral.kudi9ja.domain.wallet.LedgerService;
import com.quadrilateral.kudi9ja.domain.wallet.TxKind;
import com.quadrilateral.kudi9ja.finance.Finance;
import com.quadrilateral.kudi9ja.web.dto.SavingsDtos;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opening, funding, maturing and closing savings plans.
 *
 * <p>The two products behave differently on purpose, and the difference is the
 * whole design:
 *
 * <ul>
 *   <li><b>Fixed Savings</b> is priced by the day and pays its return
 *       <b>upfront</b>. That is only sound because the principal cannot be
 *       taken back out — so a Fixed plan cannot be broken, and
 *       {@link #breakPlan} refuses one.
 *   <li><b>Target Savings</b> pays a bonus on the final day, on what was
 *       actually saved rather than what was intended. Breaking returns every
 *       naira of principal in full and forfeits the bonus. There is no break
 *       fee and no cut of the principal.
 * </ul>
 *
 * <p>Interest is never compounded. A top-up on a Fixed plan earns the return
 * for <b>the days remaining</b>, not for the plan's original term, and is paid
 * upfront like the original.
 */
@Service
public class SavingsService {

    private static final Logger log = LoggerFactory.getLogger(SavingsService.class);

    private final SavingsPlanRepository plans;
    private final LedgerService ledger;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final AuthService auth;

    public SavingsService(
            SavingsPlanRepository plans,
            LedgerService ledger,
            SettingsService settings,
            NotificationService notifications,
            AuthService auth) {
        this.plans = plans;
        this.ledger = ledger;
        this.settings = settings;
        this.notifications = notifications;
        this.auth = auth;
    }

    // ── Quotes ─────────────────────────────────────────────────────────────

    /**
     * What a Fixed plan of this size and length would pay, before committing.
     *
     * <p>The client never prices a plan. It asks, and displays what it is told.
     */
    @Transactional(readOnly = true)
    public SavingsDtos.FixedQuoteResponse quoteFixed(BigDecimal principal, int days) {
        PlatformSettings s = settings.currentReadOnly();
        Instant now = Instant.now();

        BigDecimal interest = Finance.savingsInterest(s, principal, days);
        boolean withinLimits = Money.gte(principal, s.getMinSavingsAmount())
                && Money.lte(principal, s.getMaxSavingsAmount())
                && days >= s.getMinLockDays()
                && days <= s.getMaxLockDays();

        return new SavingsDtos.FixedQuoteResponse(
                Money.of(principal),
                days,
                s.getSavingsAnnualRate(),
                interest,
                Money.add(principal, interest),
                Finance.effectiveYieldPct(s, days),
                Dates.addDays(now, days),
                withinLimits,
                withinLimits
                        ? "The return is paid into your wallet the moment the plan opens. "
                                + "A Fixed plan cannot be broken."
                        : limitNoteFixed(s, principal, days));
    }

    @Transactional(readOnly = true)
    public SavingsDtos.TargetQuoteResponse quoteTarget(
            BigDecimal goal, AutoFrequency frequency, int months) {
        PlatformSettings s = settings.currentReadOnly();
        Instant now = Instant.now();

        int runs = Finance.targetRuns(s, frequency, months);
        BigDecimal perDeposit = Finance.targetPerDeposit(s, goal, frequency, months);
        BigDecimal bonusRate = s.targetRateFor(months);
        BigDecimal bonus = Finance.targetBonus(goal, bonusRate);
        boolean withinLimits = months >= s.getMinTargetMonths() && Money.isPositive(goal);

        return new SavingsDtos.TargetQuoteResponse(
                Money.of(goal),
                months,
                frequency,
                runs,
                perDeposit,
                bonusRate,
                bonus,
                Money.add(goal, bonus),
                Dates.addMonths(now, months),
                withinLimits,
                withinLimits
                        ? "The bonus is paid on the final day, on what you actually saved. "
                                + "Breaking the plan returns every naira and forfeits the bonus."
                        : "A target plan has to run for at least " + s.getMinTargetMonths() + " months.");
    }

    // ── Fixed Savings ──────────────────────────────────────────────────────

    /**
     * Opens a Fixed plan: locks the principal and pays the whole return into
     * the wallet at once.
     *
     * <p>Both legs are written to the ledger — the lock and the interest — so a
     * customer can see what happened rather than a single net figure.
     */
    @Transactional
    public SavingsPlan createFixed(UUID userId, SavingsDtos.CreateFixedRequest request) {
        settings.requireNotInMaintenance();
        settings.requireSavingsEnabled();
        auth.verifyPin(userId, request.pin());

        PlatformSettings s = settings.currentReadOnly();
        BigDecimal principal = Money.of(request.principal());
        int days = request.days();

        requireAmountWithin(principal, s.getMinSavingsAmount(), s.getMaxSavingsAmount(), "plan");
        if (days < s.getMinLockDays()) {
            throw new ApiException(
                    ErrorCode.LOCK_TOO_SHORT,
                    "The shortest lock is " + s.getMinLockDays() + " days.");
        }
        if (days > s.getMaxLockDays()) {
            throw new ApiException(
                    ErrorCode.LOCK_TOO_LONG,
                    "The longest lock is " + s.getMaxLockDays() + " days.");
        }

        Instant now = Instant.now();
        BigDecimal interest = Finance.savingsInterest(s, principal, days);

        SavingsPlan plan = new SavingsPlan();
        plan.setId(UUID.randomUUID());
        plan.setUserId(userId);
        plan.setTitle(request.title().trim());
        plan.setType(SavingsType.FIXED);
        plan.setPrincipal(principal);
        plan.setLockDays(days);
        plan.setInterestPaid(interest);
        // Frozen here. A later rate change never reaches this plan.
        plan.setAnnualRate(s.getSavingsAnnualRate());
        plan.setStartDate(now);
        plan.setMaturityDate(Dates.addDays(now, days));
        plan.setStatus(SavingsStatus.ACTIVE);
        plan.setEmoji(request.emoji() == null || request.emoji().isBlank() ? "🔒" : request.emoji());
        plan.setBonusRate(BigDecimal.ZERO);
        plan.setContributions(1);
        SavingsPlan saved = plans.save(plan);

        // The debit runs first: a wallet too short to fund the plan must refuse
        // before any interest is credited.
        ledger.debit(userId, principal, TxKind.SAVINGS_LOCK,
                LedgerService.Entry.of("Locked into \"" + saved.getTitle() + "\"")
                        .related(LedgerService.Related.SAVINGS_PLAN, saved.getId()));

        ledger.credit(userId, interest, TxKind.INTEREST_PAYOUT,
                LedgerService.Entry.of(
                                "Upfront " + ratePct(s.getSavingsAnnualRate())
                                        + " return on \"" + saved.getTitle() + "\"",
                                "Kudi9ja")
                        .related(LedgerService.Related.SAVINGS_PLAN, saved.getId()));

        notifications.push(
                userId,
                NotifyKind.INTEREST,
                "Fixed Savings opened",
                "Your " + Money.naira(interest) + " return is already in your wallet. The principal is "
                        + "locked until " + Dates.lagosDate(saved.getMaturityDate()) + ".",
                interest);

        log.info("Opened fixed plan {} for {} days, principal {}", saved.getId(), days, principal);
        return saved;
    }

    // ── Target Savings ─────────────────────────────────────────────────────

    /**
     * Opens a Target plan.
     *
     * <p>Nothing is debited here. The plan starts at zero and fills from the
     * schedule; the bonus lands on the final day.
     */
    @Transactional
    public SavingsPlan createTarget(UUID userId, SavingsDtos.CreateTargetRequest request) {
        settings.requireNotInMaintenance();
        settings.requireSavingsEnabled();
        auth.verifyPin(userId, request.pin());

        PlatformSettings s = settings.currentReadOnly();
        BigDecimal goal = Money.of(request.goal());
        int months = request.months();

        if (months < s.getMinTargetMonths()) {
            throw new ApiException(
                    ErrorCode.TERM_TOO_SHORT,
                    "A target plan has to run for at least " + s.getMinTargetMonths() + " months.");
        }
        requireAmountWithin(goal, s.getMinSavingsAmount(), s.getMaxSavingsAmount(), "goal");

        Instant now = Instant.now();
        BigDecimal perDeposit = Finance.targetPerDeposit(s, goal, request.frequency(), months);
        BigDecimal bonusRate = s.targetRateFor(months);
        Instant maturity = Dates.addMonths(now, months);

        SavingsPlan plan = new SavingsPlan();
        plan.setId(UUID.randomUUID());
        plan.setUserId(userId);
        plan.setTitle(request.title().trim());
        plan.setType(SavingsType.TARGET);
        plan.setPrincipal(Money.zero());
        // The term is chosen in months; the plan records the real span in days
        // so every screen can speak the same unit.
        plan.setLockDays(Dates.daysBetween(now, maturity));
        plan.setInterestPaid(Money.zero());
        plan.setAnnualRate(s.getSavingsAnnualRate());
        plan.setStartDate(now);
        plan.setMaturityDate(maturity);
        plan.setStatus(SavingsStatus.ACTIVE);
        plan.setEmoji(request.emoji() == null || request.emoji().isBlank() ? "🎯" : request.emoji());
        plan.setTargetAmount(goal);
        plan.setTargetMonths(months);
        plan.setAutoFrequency(request.frequency());
        plan.setAutoAmount(perDeposit);
        plan.setNextAutoRun(now.plus(request.frequency().interval()));
        plan.setAutoEnabled(true);
        // Frozen at creation, like a loan's rate.
        plan.setBonusRate(bonusRate);
        plan.setContributions(0);
        SavingsPlan saved = plans.save(plan);

        notifications.push(
                userId,
                NotifyKind.AUTO_SAVE,
                "Target Savings started",
                "We will move " + Money.naira(perDeposit) + " " + request.frequency().adverb()
                        + " towards your " + Money.naira(goal) + " goal. Finish it and you collect a "
                        + ratePct(bonusRate) + " bonus.");

        log.info("Opened target plan {} over {} months, goal {}", saved.getId(), months, goal);
        return saved;
    }

    // ── Funding ────────────────────────────────────────────────────────────

    /**
     * A manual top-up. Both products accept extra money.
     *
     * <p>On a <b>Fixed</b> plan the top-up earns its own return, pro-rated over
     * the days still to run rather than the plan's original term, and that
     * interest is paid to the wallet at once — the same deal as the opening
     * deposit, priced honestly for the time the money will actually be locked.
     *
     * <p>On a <b>Target</b> plan it simply counts towards the total, and so
     * towards the bonus at the end.
     */
    @Transactional
    public SavingsPlan topUp(UUID userId, UUID planId, BigDecimal amount, String pin) {
        settings.requireNotInMaintenance();
        auth.verifyPin(userId, pin);

        SavingsPlan plan = require(userId, planId);
        if (!plan.isOpen()) {
            throw new ApiException(ErrorCode.PLAN_CLOSED, "That plan is closed.");
        }
        BigDecimal topUp = Money.of(amount);
        if (Money.isZeroOrLess(topUp)) {
            throw ApiException.validation("An amount must be above zero.");
        }

        PlatformSettings s = settings.currentReadOnly();
        if (Money.gt(Money.add(plan.getPrincipal(), topUp), s.getMaxSavingsAmount())) {
            throw new ApiException(
                    ErrorCode.AMOUNT_TOO_LARGE,
                    "A plan cannot hold more than " + Money.naira(s.getMaxSavingsAmount()) + ".");
        }

        Instant now = Instant.now();

        ledger.debit(userId, topUp, TxKind.SAVINGS_LOCK,
                LedgerService.Entry.of("Top-up on \"" + plan.getTitle() + "\"")
                        .related(LedgerService.Related.SAVINGS_PLAN, plan.getId()));

        plan.setPrincipal(Money.add(plan.getPrincipal(), topUp));
        plan.setContributions(plan.getContributions() + 1);

        if (plan.isFixed()) {
            int daysLeft = Math.min(
                    Dates.daysBetween(now, plan.getMaturityDate()), s.getMaxLockDays());
            // Priced on the plan's own frozen rate, over the days it will
            // actually be locked.
            PlatformSettings planTerms = withRate(s, plan.getAnnualRate());
            BigDecimal interest = Finance.savingsInterest(planTerms, topUp, daysLeft);

            plan.setInterestPaid(Money.add(plan.getInterestPaid(), interest));
            plans.save(plan);

            if (Money.isPositive(interest)) {
                ledger.credit(userId, interest, TxKind.INTEREST_PAYOUT,
                        LedgerService.Entry.of(
                                        "Upfront return on top-up to \"" + plan.getTitle() + "\"", "Kudi9ja")
                                .related(LedgerService.Related.SAVINGS_PLAN, plan.getId()));
            }

            notifications.push(
                    userId,
                    NotifyKind.INTEREST,
                    "Top-up added",
                    "Your " + Money.naira(interest) + " return on this top-up is already in your wallet. "
                            + "It was priced over the " + daysLeft + " days this plan still has to run.",
                    interest);
            return plan;
        }

        plans.save(plan);
        notifications.push(
                userId,
                NotifyKind.AUTO_SAVE,
                "Added to \"" + plan.getTitle() + "\"",
                Money.naira(topUp) + " went in. You have saved " + Money.naira(plan.getPrincipal())
                        + " of your " + Money.naira(plan.getTargetAmount()) + " goal.",
                topUp);
        return plan;
    }

    /** Turns an auto-save schedule on or off, or changes what it moves. */
    @Transactional
    public SavingsPlan updateAutoSave(
            UUID userId, UUID planId, boolean enabled, BigDecimal amount, AutoFrequency frequency) {

        SavingsPlan plan = require(userId, planId);
        if (!plan.isTarget()) {
            throw ApiException.validation("Only a Target plan saves on a schedule.");
        }
        if (plan.getStatus() != SavingsStatus.ACTIVE) {
            throw new ApiException(ErrorCode.PLAN_CLOSED, "That plan is no longer running.");
        }

        if (amount != null) {
            if (Money.isZeroOrLess(amount)) {
                throw ApiException.validation("An amount must be above zero.");
            }
            plan.setAutoAmount(Money.of(amount));
        }
        if (frequency != null) {
            plan.setAutoFrequency(frequency);
        }

        plan.setAutoEnabled(enabled);
        plan.setNextAutoRun(enabled
                ? Instant.now().plus(plan.getAutoFrequency().interval())
                : null);
        return plans.save(plan);
    }

    // ── Closing ────────────────────────────────────────────────────────────

    /**
     * Releases a matured plan into the wallet.
     *
     * <p>A Fixed plan returns its principal — the return was paid on day one. A
     * Target plan returns its principal plus the bonus it earned by running to
     * term, on what was actually saved.
     */
    @Transactional
    public SavingsDtos.ReleaseResponse withdraw(UUID userId, UUID planId, String pin) {
        settings.requireNotInMaintenance();
        auth.verifyPin(userId, pin);

        SavingsPlan plan = require(userId, planId);
        Instant now = Instant.now();

        if (!plan.isOpen()) {
            throw new ApiException(ErrorCode.PLAN_CLOSED, "That plan has already been closed.");
        }
        if (!plan.isMature(now) && plan.getStatus() != SavingsStatus.MATURED) {
            throw new ApiException(
                    ErrorCode.PLAN_NOT_MATURED,
                    plan.isFixed()
                            ? "This plan matures on " + Dates.lagosDate(plan.getMaturityDate())
                                    + ". A Fixed plan cannot be broken — the return was paid upfront "
                                    + "because the principal stays put."
                            : "This plan matures on " + Dates.lagosDate(plan.getMaturityDate())
                                    + ". You can break it early, but the bonus is forfeited.");
        }

        BigDecimal principal = plan.getPrincipal();
        BigDecimal bonus = plan.isTarget() && !plan.isBonusPaid()
                ? plan.bonusEarned()
                : Money.zero();

        plan.setStatus(SavingsStatus.WITHDRAWN);
        plan.setBonusPaid(true);
        plan.setAutoEnabled(false);
        plan.setNextAutoRun(null);
        plan.setInterestPaid(Money.add(plan.getInterestPaid(), bonus));
        plan.setClosedAt(now);
        plans.save(plan);

        if (Money.isPositive(principal)) {
            ledger.credit(userId, principal, TxKind.SAVINGS_RELEASE,
                    LedgerService.Entry.of("Matured savings released: \"" + plan.getTitle() + "\"")
                            .related(LedgerService.Related.SAVINGS_PLAN, plan.getId()));
        }
        if (Money.isPositive(bonus)) {
            ledger.credit(userId, bonus, TxKind.INTEREST_PAYOUT,
                    LedgerService.Entry.of(
                                    ratePct(plan.getBonusRate()) + " completion bonus on \""
                                            + plan.getTitle() + "\"", "Kudi9ja")
                            .related(LedgerService.Related.SAVINGS_PLAN, plan.getId()));
            notifications.push(
                    userId,
                    NotifyKind.INTEREST,
                    "Bonus paid",
                    "You finished \"" + plan.getTitle() + "\" and earned " + Money.naira(bonus) + ".",
                    bonus);
        }

        BigDecimal credited = Money.add(principal, bonus);
        return new SavingsDtos.ReleaseResponse(
                plan.getId(),
                principal,
                bonus,
                credited,
                ledger.balanceOf(userId),
                plan.getStatus(),
                Money.naira(credited) + " is back in your wallet.");
    }

    /**
     * Breaks a Target plan early.
     *
     * <p>Every naira saved comes back, in full. There is no break fee and no cut
     * of the principal — only the bonus is forfeited, which is the whole
     * incentive the plan was built on.
     *
     * <p>A Fixed plan cannot be broken and this refuses one rather than quietly
     * doing nothing, so the customer is told why.
     */
    @Transactional
    public SavingsDtos.ReleaseResponse breakPlan(UUID userId, UUID planId, String pin) {
        settings.requireNotInMaintenance();
        auth.verifyPin(userId, pin);

        SavingsPlan plan = require(userId, planId);
        if (plan.isFixed()) {
            throw new ApiException(
                    ErrorCode.PLAN_CANNOT_BREAK,
                    "A Fixed Savings plan cannot be broken. The return was paid into your wallet the day "
                            + "it opened, which is only possible because the principal stays put until "
                            + Dates.lagosDate(plan.getMaturityDate()) + ".");
        }
        if (!plan.canBreak()) {
            throw new ApiException(ErrorCode.PLAN_CLOSED, "That plan is no longer running.");
        }

        BigDecimal principal = plan.getPrincipal();
        plan.setStatus(SavingsStatus.BROKEN);
        plan.setAutoEnabled(false);
        plan.setNextAutoRun(null);
        plan.setClosedAt(Instant.now());
        plan.setClosureNote("Broken early by the customer. Bonus forfeited.");
        plans.save(plan);

        if (Money.isPositive(principal)) {
            ledger.credit(userId, principal, TxKind.SAVINGS_RELEASE,
                    LedgerService.Entry.of("Broken early: \"" + plan.getTitle() + "\"")
                            .related(LedgerService.Related.SAVINGS_PLAN, plan.getId()));
        }

        notifications.push(
                userId,
                NotifyKind.GENERAL,
                "Savings broken",
                "Your " + Money.naira(principal) + " is back in your wallet. The "
                        + ratePct(plan.getBonusRate()) + " bonus was forfeited.",
                principal);

        return new SavingsDtos.ReleaseResponse(
                plan.getId(),
                principal,
                Money.zero(),
                principal,
                ledger.balanceOf(userId),
                plan.getStatus(),
                Money.naira(principal) + " is back in your wallet. The bonus was forfeited.");
    }

    /**
     * Releases a Fixed plan early on death or permanent incapacity, in full and
     * without penalty, as the Terms provide.
     *
     * <p>This is the only early release a Fixed plan has, and it is an admin
     * action taken on evidence — never something a customer can trigger.
     */
    @Transactional
    public SavingsDtos.ReleaseResponse releaseOnCompassionateGrounds(
            UUID planId, String reason, String actor) {

        SavingsPlan plan = plans.findById(planId)
                .orElseThrow(() -> ApiException.notFound("That plan"));
        if (!plan.isOpen()) {
            throw new ApiException(ErrorCode.PLAN_CLOSED, "That plan is already closed.");
        }

        BigDecimal principal = plan.getPrincipal();
        BigDecimal bonus = plan.isTarget() && !plan.isBonusPaid() ? plan.bonusEarned() : Money.zero();

        plan.setStatus(SavingsStatus.RELEASED_ON_COMPASSIONATE_GROUNDS);
        plan.setAutoEnabled(false);
        plan.setNextAutoRun(null);
        plan.setBonusPaid(true);
        plan.setClosedAt(Instant.now());
        plan.setClosureNote("Released early without penalty by " + actor + ". " + reason);
        plans.save(plan);

        if (Money.isPositive(principal)) {
            ledger.credit(plan.getUserId(), principal, TxKind.SAVINGS_RELEASE,
                    LedgerService.Entry.of("Released early without penalty: \"" + plan.getTitle() + "\"")
                            .related(LedgerService.Related.SAVINGS_PLAN, plan.getId()));
        }
        if (Money.isPositive(bonus)) {
            ledger.credit(plan.getUserId(), bonus, TxKind.INTEREST_PAYOUT,
                    LedgerService.Entry.of("Bonus on early release", "Kudi9ja")
                            .related(LedgerService.Related.SAVINGS_PLAN, plan.getId()));
        }

        BigDecimal credited = Money.add(principal, bonus);
        return new SavingsDtos.ReleaseResponse(
                plan.getId(), principal, bonus, credited,
                ledger.balanceOf(plan.getUserId()), plan.getStatus(),
                "Released in full without penalty.");
    }

    // ── Sweeps ─────────────────────────────────────────────────────────────

    /**
     * Flips plans that have reached their maturity date.
     *
     * <p>The client used to do this lazily, when the app was next opened. That
     * meant a plan matured the moment its owner looked at it, and never for
     * somebody who did not — which is fine for a demo and wrong for money. The
     * server sweeps on the hour so a maturity happens on the day it is due
     * whether anyone is watching or not.
     *
     * <p>Maturing does not release the money. It flips the status, stops the
     * auto-save and tells the customer; releasing is still their action, so the
     * principal lands in the wallet when they ask for it rather than sitting
     * there unannounced.
     */
    @Transactional
    public int sweepMatured(Instant now) {
        List<SavingsPlan> due = plans.findMatured(now);

        for (SavingsPlan plan : due) {
            plan.setStatus(SavingsStatus.MATURED);
            plan.setAutoEnabled(false);
            plan.setNextAutoRun(null);
            plans.save(plan);

            BigDecimal bonus = plan.isTarget() && !plan.isBonusPaid()
                    ? plan.bonusEarned()
                    : Money.zero();

            notifications.push(
                    plan.getUserId(),
                    NotifyKind.MATURITY,
                    "\"" + plan.getTitle() + "\" has matured",
                    plan.isTarget() && Money.isPositive(bonus)
                            ? "Your " + Money.naira(plan.getPrincipal()) + " is ready, plus a "
                                    + Money.naira(bonus) + " completion bonus. Withdraw it whenever you like."
                            : "Your " + Money.naira(plan.getPrincipal())
                                    + " is ready to move back into your wallet.",
                    Money.add(plan.getPrincipal(), bonus));
        }

        if (!due.isEmpty()) {
            log.info("Maturity sweep flipped {} plan(s) to matured", due.size());
        }
        return due.size();
    }

    /**
     * Runs the target contributions that have come due.
     *
     * <p>A short balance is <b>skipped, not failed</b>: the run is rescheduled
     * for the next cycle, the customer is told, and the bonus at the end is
     * paid on what was actually saved. Nothing is overdrawn and no fee is
     * charged for missing one — a customer who is short this week is the last
     * person who should be charged for it.
     */
    @Transactional
    public AutoSaveRun runDueAutoSaves(Instant now) {
        List<SavingsPlan> due = plans.findDueAutoSaves(now);
        int collected = 0;
        int skipped = 0;

        for (SavingsPlan plan : due) {
            BigDecimal amount = Money.of(plan.getAutoAmount());
            if (Money.isZeroOrLess(amount)) {
                plan.setAutoEnabled(false);
                plan.setNextAutoRun(null);
                plans.save(plan);
                continue;
            }

            BigDecimal balance = ledger.balanceOf(plan.getUserId());
            if (Money.gt(amount, balance)) {
                plan.setMissedContributions(plan.getMissedContributions() + 1);
                plan.setNextAutoRun(now.plus(plan.getAutoFrequency().interval()));
                plans.save(plan);
                skipped++;

                notifications.push(
                        plan.getUserId(),
                        NotifyKind.AUTO_SAVE,
                        "Auto-save skipped",
                        "We could not move " + Money.naira(amount) + " into \"" + plan.getTitle()
                                + "\" — your wallet held " + Money.naira(balance) + ". "
                                + "Nothing was charged, and we will try again next time.",
                        amount);
                continue;
            }

            ledger.debit(plan.getUserId(), amount, TxKind.SAVINGS_LOCK,
                    LedgerService.Entry.of("Auto-save into \"" + plan.getTitle() + "\"")
                            .related(LedgerService.Related.SAVINGS_PLAN, plan.getId()));

            plan.setPrincipal(Money.add(plan.getPrincipal(), amount));
            plan.setContributions(plan.getContributions() + 1);
            plan.setNextAutoRun(now.plus(plan.getAutoFrequency().interval()));
            plans.save(plan);
            collected++;

            notifications.push(
                    plan.getUserId(),
                    NotifyKind.AUTO_SAVE,
                    "Saved " + Money.naira(amount),
                    Money.naira(amount) + " went into \"" + plan.getTitle() + "\". You have saved "
                            + Money.naira(plan.getPrincipal()) + " of your "
                            + Money.naira(plan.getTargetAmount()) + " goal.",
                    amount);
        }

        if (collected > 0 || skipped > 0) {
            log.info("Auto-save run: {} collected, {} skipped for a short balance", collected, skipped);
        }
        return new AutoSaveRun(due.size(), collected, skipped);
    }

    /** What one pass of the auto-save job did. */
    public record AutoSaveRun(int due, int collected, int skipped) {
    }

    // ── Reads ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SavingsPlan> list(UUID userId) {
        return plans.findByUserIdOrderByStartDateDesc(userId);
    }

    @Transactional(readOnly = true)
    public SavingsPlan get(UUID userId, UUID planId) {
        return require(userId, planId);
    }

    @Transactional(readOnly = true)
    public BigDecimal totalSaved(UUID userId) {
        return Money.of(plans.totalSaved(userId));
    }

    @Transactional(readOnly = true)
    public long plansOpened(UUID userId) {
        return plans.countByUserId(userId);
    }

    // ── Internals ──────────────────────────────────────────────────────────

    private SavingsPlan require(UUID userId, UUID planId) {
        return plans.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> ApiException.notFound("That plan"));
    }

    private static void requireAmountWithin(
            BigDecimal amount, BigDecimal min, BigDecimal max, String what) {
        if (Money.lt(amount, min)) {
            throw new ApiException(
                    ErrorCode.AMOUNT_TOO_SMALL,
                    "The smallest " + what + " is " + Money.naira(min) + ".",
                    Map.of("minimum", min));
        }
        if (Money.gt(amount, max)) {
            throw new ApiException(
                    ErrorCode.AMOUNT_TOO_LARGE,
                    "The largest " + what + " is " + Money.naira(max) + ".",
                    Map.of("maximum", max));
        }
    }

    private static String limitNoteFixed(PlatformSettings s, BigDecimal principal, int days) {
        if (Money.lt(principal, s.getMinSavingsAmount())) {
            return "The smallest plan is " + Money.naira(s.getMinSavingsAmount()) + ".";
        }
        if (Money.gt(principal, s.getMaxSavingsAmount())) {
            return "The largest plan is " + Money.naira(s.getMaxSavingsAmount()) + ".";
        }
        if (days < s.getMinLockDays()) {
            return "The shortest lock is " + s.getMinLockDays() + " days.";
        }
        return "The longest lock is " + s.getMaxLockDays() + " days.";
    }

    /**
     * The settings as this plan was priced, so a top-up uses the plan's own
     * frozen rate rather than today's.
     */
    private static PlatformSettings withRate(PlatformSettings live, BigDecimal planRate) {
        if (planRate == null || planRate.compareTo(live.getSavingsAnnualRate()) == 0) {
            return live;
        }
        PlatformSettings terms = new PlatformSettings();
        terms.setSavingsAnnualRate(planRate);
        terms.setDaysPerYear(live.getDaysPerYear());
        return terms;
    }

    /** "17%", "12.5%" — never a rate rounded to "13%". */
    static String ratePct(BigDecimal rate) {
        if (rate == null) {
            return "0%";
        }
        BigDecimal pct = rate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return pct.toPlainString() + "%";
    }
}
