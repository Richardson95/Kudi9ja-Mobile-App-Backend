package com.quadrilateral.kudi9ja.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

/**
 * Runs {@code @Async} work on the calling thread.
 *
 * <p>Push delivery is deliberately asynchronous in production: it happens after
 * the transaction commits, and leaving it on the request thread means a slow
 * provider is paid for by the customer waiting on the screen. That is right for
 * a customer and useless for a test, which would assert against a push that has
 * not been sent yet and fail on timing rather than on behaviour.
 *
 * <p>So the threading is removed rather than waited on. What these tests are
 * about is what was sent and to whom — the handover to the provider is the same
 * call either way.
 */
public class InlineAsync {

    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        public TaskExecutor inlineTaskExecutor() {
            return new SyncTaskExecutor();
        }
    }
}
