package com.quadrilateral.kudi9ja.jobs;

import com.quadrilateral.kudi9ja.config.Kudi9jaProperties;
import com.quadrilateral.kudi9ja.domain.audit.AuditCategory;
import com.quadrilateral.kudi9ja.domain.audit.AuditService;
import com.quadrilateral.kudi9ja.domain.loan.LoanService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The lending sweeps.
 *
 * <p>Note what neither of these does: <b>neither changes what anybody owes</b>.
 * Going overdue moves a status and sends a message. There is no late fee and no
 * penalty interest in this product, and the Lending Agreement commits to that
 * in the words "even in default, the amount you owe does not increase" — so a
 * job that quietly grew a balance would break a written promise, not just a
 * product rule.
 */
@Component
@ConditionalOnProperty(prefix = "kudi9ja.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LendingJobs {

    private static final Logger log = LoggerFactory.getLogger(LendingJobs.class);

    private final LoanService loans;
    private final AuditService audit;
    private final Kudi9jaProperties properties;

    public LendingJobs(LoanService loans, AuditService audit, Kudi9jaProperties properties) {
        this.loans = loans;
        this.audit = audit;
        this.properties = properties;
    }

    /** Marks loans past their due date with a balance still outstanding. */
    @Scheduled(cron = "0 25 * * * *", zone = "Africa/Lagos")
    public void sweepOverdue() {
        try {
            int overdue = loans.sweepOverdue(Instant.now());
            if (overdue > 0) {
                audit.recordSystem(
                        AuditCategory.LOAN,
                        "Overdue sweep",
                        overdue + (overdue == 1 ? " loan" : " loans")
                                + " passed the due date with a balance outstanding. "
                                + "No fee or penalty interest was applied — there is none in this product.");
            }
        } catch (RuntimeException e) {
            log.error("Overdue sweep failed", e);
        }
    }

    /**
     * Reminds borrowers of an instalment coming up.
     *
     * <p>Runs at 9am Lagos time: inside the 8am–8pm contact window the Privacy
     * Policy commits to, and early enough in the day to be acted on.
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Africa/Lagos")
    public void sendRepaymentReminders() {
        try {
            int days = properties.jobs().repaymentReminderDays();
            int sent = loans.remindUpcomingRepayments(Instant.now(), days);
            if (sent > 0) {
                audit.recordSystem(
                        AuditCategory.LOAN,
                        "Repayment reminders",
                        sent + " borrower(s) were reminded of an instalment due within "
                                + days + (days == 1 ? " day." : " days."));
            }
        } catch (RuntimeException e) {
            log.error("Repayment reminders failed", e);
        }
    }
}
