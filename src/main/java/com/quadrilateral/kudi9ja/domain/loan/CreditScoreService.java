package com.quadrilateral.kudi9ja.domain.loan;

import com.quadrilateral.kudi9ja.common.util.Money;
import com.quadrilateral.kudi9ja.domain.savings.SavingsPlanRepository;
import com.quadrilateral.kudi9ja.domain.settings.PlatformSettings;
import com.quadrilateral.kudi9ja.domain.settings.SettingsService;
import com.quadrilateral.kudi9ja.domain.user.KycTier;
import com.quadrilateral.kudi9ja.domain.user.User;
import com.quadrilateral.kudi9ja.domain.user.UserRepository;
import com.quadrilateral.kudi9ja.finance.Finance;
import com.quadrilateral.kudi9ja.web.dto.LoanDtos;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kudi9ja's own view of a customer, out of 850.
 *
 * <p>Built only from what the customer has actually done here: plans opened,
 * money saved, loans repaid, anything overdue, and whether their identity is
 * confirmed. Nothing is inferred from anywhere else.
 *
 * <p>This is <b>not a credit-bureau score</b>. The Privacy Policy says so, and
 * says the customer may demand a human review of any automated decision that
 * goes against them — which is why the breakdown is served alongside the number
 * rather than kept internal. A customer who is refused should be able to see
 * what refused them.
 */
@Service
public class CreditScoreService {

    private final UserRepository users;
    private final SavingsPlanRepository plans;
    private final LoanRepository loans;
    private final SettingsService settings;

    public CreditScoreService(
            UserRepository users,
            SavingsPlanRepository plans,
            LoanRepository loans,
            SettingsService settings) {
        this.users = users;
        this.plans = plans;
        this.loans = loans;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public Assessment assess(UUID userId) {
        PlatformSettings s = settings.currentReadOnly();
        User user = users.findById(userId)
                .orElseThrow(() -> com.quadrilateral.kudi9ja.common.error.ApiException.notFound("That account"));

        int plansOpened = (int) plans.countByUserId(userId);
        BigDecimal totalSaved = Money.of(plans.totalSaved(userId));
        int loansRepaid = (int) loans.countByUserIdAndStatus(userId, LoanStatus.REPAID);
        boolean anyOverdue = loans.existsByUserIdAndStatus(userId, LoanStatus.OVERDUE);
        boolean fullyVerified = user.getKycTier() == KycTier.TIER2;

        int score = Finance.creditScore(
                s, plansOpened, totalSaved, loansRepaid, anyOverdue, fullyVerified);

        return new Assessment(
                score,
                Finance.creditBand(score),
                plansOpened,
                totalSaved,
                loansRepaid,
                anyOverdue,
                fullyVerified,
                Money.of(loans.openPrincipal(userId)));
    }

    /** The score with the breakdown behind it, for the customer's own screen. */
    @Transactional(readOnly = true)
    public LoanDtos.CreditScoreResponse explain(UUID userId) {
        PlatformSettings s = settings.currentReadOnly();
        Assessment assessment = assess(userId);
        return new LoanDtos.CreditScoreResponse(
                assessment.score(),
                assessment.band(),
                s.getCreditScoreFloor(),
                s.getCreditScoreCeiling(),
                factors(s, assessment),
                "This is Kudi9ja's own view, built from what you have done with us. "
                        + "It is not a credit-bureau score. If a decision goes against you, "
                        + "you can ask a person to look at it.");
    }

    /** What each part of the score is worth, and what the customer has earned. */
    public List<LoanDtos.CreditFactor> factors(PlatformSettings s, Assessment a) {
        int savingsPoints = Money.calc(a.totalSaved())
                .divide(s.getCreditNairaPerSavingsPoint(), 0, RoundingMode.FLOOR)
                .intValue();

        return List.of(
                LoanDtos.CreditFactor.of(
                        "Identity verified",
                        a.fullyVerified() ? "BVN and NIN confirmed" : "Complete your verification",
                        a.fullyVerified() ? s.getCreditVerifiedBonus() : 0,
                        s.getCreditVerifiedBonus()),

                LoanDtos.CreditFactor.of(
                        "Savings habit",
                        a.plansOpened() + (a.plansOpened() == 1 ? " plan opened" : " plans opened"),
                        clamp(a.plansOpened() * s.getCreditPointsPerPlan(), 0, s.getCreditPlanPointsCap()),
                        s.getCreditPlanPointsCap()),

                LoanDtos.CreditFactor.of(
                        "Amount saved",
                        Money.naira(a.totalSaved()) + " locked away",
                        clamp(savingsPoints, 0, s.getCreditSavingsPointsCap()),
                        s.getCreditSavingsPointsCap()),

                LoanDtos.CreditFactor.of(
                        "Repayment history",
                        a.loansRepaid() == 0
                                ? "No loans repaid yet"
                                : a.loansRepaid() + (a.loansRepaid() == 1 ? " loan" : " loans")
                                        + " repaid in full",
                        clamp(a.loansRepaid() * s.getCreditPointsPerRepaidLoan(),
                                0, s.getCreditRepaidPointsCap()),
                        s.getCreditRepaidPointsCap()),

                LoanDtos.CreditFactor.of(
                        "Nothing overdue",
                        a.anyOverdue() ? "You have an overdue loan" : "All repayments on time",
                        a.anyOverdue() ? -s.getCreditOverduePenalty() : 0,
                        0,
                        a.anyOverdue()));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Everything the score was computed from, kept together for the record. */
    public record Assessment(
            int score,
            String band,
            int plansOpened,
            BigDecimal totalSaved,
            int loansRepaid,
            boolean anyOverdue,
            boolean fullyVerified,
            BigDecimal openPrincipal) {
    }
}
