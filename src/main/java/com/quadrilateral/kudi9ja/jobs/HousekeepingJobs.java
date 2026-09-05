package com.quadrilateral.kudi9ja.jobs;

import com.quadrilateral.kudi9ja.common.idempotency.IdempotencyService;
import com.quadrilateral.kudi9ja.domain.kyc.OtpService;
import com.quadrilateral.kudi9ja.domain.notification.NotificationService;
import com.quadrilateral.kudi9ja.domain.user.SignupService;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sweeping up after the short-lived records.
 *
 * <p>These three tables all hold things that stop being useful on a clock:
 * a half-finished sign-up nobody came back to, a one-time code that has
 * expired, an idempotency key from a request no client will retry. Leaving them
 * costs storage, but more to the point an expired sign-up draft holds a BVN and
 * an NIN, and identity data nobody needs is data best not kept — the Privacy
 * Policy commits to holding it only as long as the purpose lasts.
 *
 * <p>Deliberately not here: anything on the ledger, a pay-in claim, a
 * withdrawal, a loan, or an audit entry. Those are transaction records, kept
 * five years under the AML rules, and the audit log is append-only with no
 * deletion at all.
 */
@Component
@ConditionalOnProperty(prefix = "kudi9ja.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HousekeepingJobs {

    private static final Logger log = LoggerFactory.getLogger(HousekeepingJobs.class);

    /**
     * How long an idempotency key stays useful. Well past any client's retry
     * window, and short enough that the table does not become a second ledger.
     */
    private static final Duration IDEMPOTENCY_RETENTION = Duration.ofDays(7);

    private final SignupService signups;
    private final OtpService otps;
    private final IdempotencyService idempotency;
    private final NotificationService notifications;

    public HousekeepingJobs(
            SignupService signups,
            OtpService otps,
            IdempotencyService idempotency,
            NotificationService notifications) {
        this.signups = signups;
        this.otps = otps;
        this.idempotency = idempotency;
        this.notifications = notifications;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Africa/Lagos")
    public void purgeExpiredRecords() {
        Instant now = Instant.now();
        try {
            int drafts = signups.purgeExpiredDrafts(now);
            int codes = otps.purgeExpired(now);
            int keys = idempotency.purgeOlderThan(now.minus(IDEMPOTENCY_RETENTION));

            // Handsets nobody has opened in months. Left alone, a device
            // register becomes mostly phones that no longer exist, and every
            // notification pays for the dead ones.
            int devices = notifications.purgeStaleDevices(now);

            if (drafts + codes + keys + devices > 0) {
                log.info(
                        "Housekeeping removed {} expired sign-up draft(s), {} spent code(s), "
                                + "{} idempotency key(s), {} stale device(s)",
                        drafts, codes, keys, devices);
            }
        } catch (RuntimeException e) {
            log.error("Housekeeping sweep failed", e);
        }
    }
}
