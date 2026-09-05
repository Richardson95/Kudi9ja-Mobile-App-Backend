package com.quadrilateral.kudi9ja.jobs;

import com.quadrilateral.kudi9ja.common.util.Money;
import com.quadrilateral.kudi9ja.config.Kudi9jaProperties;
import com.quadrilateral.kudi9ja.domain.audit.AuditCategory;
import com.quadrilateral.kudi9ja.domain.audit.AuditService;
import com.quadrilateral.kudi9ja.domain.compliance.DataRightsService;
import com.quadrilateral.kudi9ja.domain.notification.NotificationService;
import com.quadrilateral.kudi9ja.domain.notification.NotifyKind;
import com.quadrilateral.kudi9ja.domain.payin.PayInService;
import com.quadrilateral.kudi9ja.domain.payin.UnmatchedPayIn;
import com.quadrilateral.kudi9ja.domain.user.AccountStatus;
import com.quadrilateral.kudi9ja.domain.user.User;
import com.quadrilateral.kudi9ja.domain.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The daily jobs that exist because a document promised them.
 *
 * <p>Each of these is an obligation rather than a feature. Money that arrives
 * without an owner goes back after thirty days because the Terms say so; an
 * account that has gone quiet for a year is frozen and re-verified because the
 * AML rules require it. Neither would ever be asked for by a customer, and both
 * are the sort of thing that quietly stops happening if nobody schedules it.
 */
@Component
@ConditionalOnProperty(prefix = "kudi9ja.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ComplianceJobs {

    private static final Logger log = LoggerFactory.getLogger(ComplianceJobs.class);

    private final PayInService payIns;
    private final DataRightsService dataRights;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Kudi9jaProperties properties;

    public ComplianceJobs(
            PayInService payIns,
            DataRightsService dataRights,
            UserRepository users,
            NotificationService notifications,
            AuditService audit,
            Kudi9jaProperties properties) {
        this.payIns = payIns;
        this.dataRights = dataRights;
        this.users = users;
        this.notifications = notifications;
        this.audit = audit;
        this.properties = properties;
    }

    /**
     * Returns money that arrived without an owner.
     *
     * <p>A credit into the collection account that cannot be tied to a customer
     * is held while we try to trace it and returned to source after thirty
     * days. The window is in the Terms, which is what makes it a deadline
     * rather than an intention.
     *
     * <p>The job marks the record returned; the transfer itself is a treasury
     * action outside this system. Marking it here is what puts it on the
     * audit log and out of the held queue, so the two cannot drift apart
     * silently.
     */
    @Scheduled(cron = "0 30 7 * * *", zone = "Africa/Lagos")
    public void returnUnmatchedPayIns() {
        try {
            List<UnmatchedPayIn> due = payIns.dueForReturn();
            if (due.isEmpty()) {
                return;
            }

            int days = properties.jobs().unmatchedPayInReturnDays();
            for (UnmatchedPayIn record : due) {
                payIns.markReturned(
                        record.getId(),
                        null,
                        "Held " + days + " days without being traced to a customer. "
                                + "Returned to source as the Terms require.");
            }

            audit.recordSystem(
                    AuditCategory.COMPLIANCE,
                    "Unmatched pay-ins returned",
                    due.size() + " untraced credit(s) totalling "
                            + Money.naira(due.stream()
                                    .map(UnmatchedPayIn::getAmount)
                                    .reduce(Money.zero(), Money::add))
                            + " passed the " + days + "-day hold and were returned to source.");
        } catch (RuntimeException e) {
            log.error("Unmatched pay-in return job failed", e);
        }
    }

    /**
     * Erases closed accounts whose retention period has run out.
     *
     * <p>The other half of what a customer is promised when they close an
     * account. They are told their identity is kept for five years <b>and then
     * erased</b>; without this job the first half happens and the second never
     * does, and "retained for five years" quietly means "kept for ever".
     *
     * <p>Runs before the dormancy sweep so a record that is due for erasure is
     * gone rather than being reconsidered on its way out.
     */
    @Scheduled(cron = "0 15 7 * * *", zone = "Africa/Lagos")
    public void eraseExpiredRecords() {
        try {
            dataRights.erase(Instant.now());
        } catch (RuntimeException e) {
            log.error("Retention erasure job failed", e);
        }
    }

    /**
     * Freezes accounts that have gone quiet.
     *
     * <p>Dormancy is not a punishment and nothing is taken: the wallet, the
     * savings and any loan are all untouched, and the freeze lifts on
     * re-verification. What it stops is money moving out of an account whose
     * owner has not been seen for a year, which is the case an account takeover
     * most likes.
     */
    @Scheduled(cron = "0 45 7 * * *", zone = "Africa/Lagos")
    @Transactional
    public void markDormantAccounts() {
        try {
            int months = properties.jobs().dormancyMonths();
            Instant cutoff = Instant.now().minus(months * 30L, ChronoUnit.DAYS);

            List<User> quiet = users.findDormantCandidates(cutoff);
            if (quiet.isEmpty()) {
                return;
            }

            for (User user : quiet) {
                user.setAccountStatus(AccountStatus.DORMANT);
                user.setStatusNote("No activity for " + months + " months. "
                        + "Frozen pending re-verification.");
                users.save(user);

                notifications.push(
                        user.getId(),
                        NotifyKind.SECURITY,
                        "Your account is dormant",
                        "We have not seen any activity on your account for " + months
                                + " months, so we have put it on hold. Nothing has been taken — your "
                                + "wallet, savings and any loan are exactly as you left them. "
                                + "Contact support@kudi9ja.com to verify your identity and reopen it.");
            }

            audit.recordSystem(
                    AuditCategory.COMPLIANCE,
                    "Dormancy sweep",
                    quiet.size() + " account(s) with no activity for " + months
                            + " months were marked dormant and frozen pending re-verification.");
        } catch (RuntimeException e) {
            log.error("Dormancy sweep failed", e);
        }
    }
}
