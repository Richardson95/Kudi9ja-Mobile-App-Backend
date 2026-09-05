package com.quadrilateral.kudi9ja.config;

import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;

/**
 * Turns the scheduled jobs on.
 *
 * <p>Gated on a property so a test or a local run can start the application
 * without a maturity sweep firing against a fixture database. It defaults to on
 * — a deployment that forgot to enable its jobs would silently stop maturing
 * plans and collecting auto-saves, and a silent failure of that kind is worse
 * than a noisy one.
 *
 * <p>Every cron in this application is anchored to {@code Africa/Lagos}. The
 * customers are in Nigeria, the promises are made in their day, and a server
 * that moves to another region must not quietly move a repayment reminder to
 * three in the morning.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "kudi9ja.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {

    /**
     * A small pool rather than the single thread Spring defaults to.
     *
     * <p>The sweeps are minutes apart on purpose, but a slow one must not delay
     * the next: an auto-save run that overruns should not push the overdue
     * sweep into the following hour.
     */
    @Bean
    public ConcurrentTaskScheduler taskScheduler() {
        return new ConcurrentTaskScheduler(Executors.newScheduledThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("kudi9ja-jobs-" + thread.threadId());
            thread.setDaemon(true);
            return thread;
        }));
    }
}
