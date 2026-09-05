package com.quadrilateral.kudi9ja.jobs;

import com.quadrilateral.kudi9ja.domain.audit.AuditCategory;
import com.quadrilateral.kudi9ja.domain.audit.AuditService;
import com.quadrilateral.kudi9ja.domain.savings.SavingsService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The savings sweeps, on the hour.
 *
 * <p>The Flutter client did both of these lazily, when the app was next opened.
 * That is fine for a device-local demo and wrong once the money is real: it
 * means a plan matures when its owner happens to look at it, an auto-save
 * happens when the app is opened rather than when it was scheduled, and neither
 * happens at all for a customer who has not opened the app this month.
 *
 * <p>Hourly rather than daily because both are date-driven and an hour is the
 * smallest unit either promise is made in. Both are idempotent: the queries
 * behind them return only plans that still need the work, so a run that
 * overlaps or repeats does nothing twice.
 */
@Component
@ConditionalOnProperty(prefix = "kudi9ja.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SavingsJobs {

    private static final Logger log = LoggerFactory.getLogger(SavingsJobs.class);

    private final SavingsService savings;
    private final AuditService audit;

    public SavingsJobs(SavingsService savings, AuditService audit) {
        this.savings = savings;
        this.audit = audit;
    }

    /** Flips plans past their maturity date, and stops their auto-save. */
    @Scheduled(cron = "0 5 * * * *", zone = "Africa/Lagos")
    public void sweepMaturities() {
        try {
            int matured = savings.sweepMatured(Instant.now());
            if (matured > 0) {
                audit.recordSystem(
                        AuditCategory.GENERAL,
                        "Maturity sweep",
                        matured + (matured == 1 ? " plan" : " plans")
                                + " reached maturity and are ready to be released.");
            }
        } catch (RuntimeException e) {
            // Logged rather than rethrown: a failed sweep must not stop the
            // next one from running an hour later.
            log.error("Maturity sweep failed", e);
        }
    }

    /**
     * Pulls the target contributions that have come due.
     *
     * <p>A short balance is skipped and retried next cycle. Nothing is
     * overdrawn, no fee is charged for a miss, and the bonus at the end is paid
     * on what was actually saved.
     */
    @Scheduled(cron = "0 15 * * * *", zone = "Africa/Lagos")
    public void runAutoSaves() {
        try {
            SavingsService.AutoSaveRun run = savings.runDueAutoSaves(Instant.now());
            if (run.due() > 0) {
                audit.recordSystem(
                        AuditCategory.GENERAL,
                        "Auto-save run",
                        run.due() + " contribution(s) came due: " + run.collected()
                                + " collected, " + run.skipped() + " skipped for a short balance.");
            }
        } catch (RuntimeException e) {
            log.error("Auto-save run failed", e);
        }
    }
}
