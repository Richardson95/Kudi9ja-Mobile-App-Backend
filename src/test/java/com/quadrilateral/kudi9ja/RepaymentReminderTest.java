package com.quadrilateral.kudi9ja;

import static org.assertj.core.api.Assertions.assertThat;

import com.quadrilateral.kudi9ja.domain.notification.NotifyKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Which notifications reach a customer by email as well as by push.
 *
 * <p>Push already carries everything — forty notification sites across loans,
 * thrift, savings, payments and security — and none of that changed. What is
 * pinned here is the much smaller question of which of them are *also* worth an
 * email, because getting that wrong is expensive in both directions.
 *
 * <p>Too few, and a borrower who uninstalled the app is never told an
 * instalment is due, then pays a penalty for it. Too many, and a message about
 * every auto-save trains the customer to ignore the sender — so the one that
 * finally matters arrives in a folder nobody reads. Email is a channel that
 * gets spent, and this is the record of what it is spent on.
 */
@DisplayName("Reminder emails")
class RepaymentReminderTest {

    @Nested
    @DisplayName("What earns an email")
    class WhatEarnsAnEmail {

        /**
         * The one notification with a deadline attached and a cost for missing
         * it. Push reaches a phone that has the app installed, has been opened
         * since the token last changed, and has notifications switched on at the
         * operating system. A borrower can fail all three and still owe the
         * money.
         */
        @Test
        @DisplayName("a repayment falling due is emailed")
        void repaymentDue() {
            assertThat(NotifyKind.REPAYMENT_DUE.alsoEmail()).isTrue();
        }

        /**
         * The phone is exactly what an attacker has. Somebody who has taken over
         * an account can silence push by signing the customer's handset out;
         * they cannot silence the customer's inbox.
         */
        @Test
        @DisplayName("a security alert is emailed")
        void security() {
            assertThat(NotifyKind.SECURITY.alsoEmail()).isTrue();
        }
    }

    @Nested
    @DisplayName("What does not")
    class WhatDoesNot {

        /**
         * All of these still push — that is not in question. They are simply not
         * worth an email each, and sending one would cost the reminders above
         * the attention they depend on.
         */
        @Test
        @DisplayName("routine money movements stay on push alone")
        void routine() {
            assertThat(NotifyKind.INTEREST.alsoEmail()).isFalse();
            assertThat(NotifyKind.MATURITY.alsoEmail()).isFalse();
            assertThat(NotifyKind.AUTO_SAVE.alsoEmail()).isFalse();
            assertThat(NotifyKind.THRIFT.alsoEmail()).isFalse();
            assertThat(NotifyKind.REPAYMENT_PAID.alsoEmail()).isFalse();
            assertThat(NotifyKind.GENERAL.alsoEmail()).isFalse();
        }

        /**
         * A guard against the flag being switched on everywhere by a future
         * edit, which would be the easy mistake and the one nobody notices until
         * customers start filtering the sender into spam.
         */
        @Test
        @DisplayName("email stays the exception, not the rule")
        void stillTheException() {
            long emailed = java.util.Arrays.stream(NotifyKind.values())
                    .filter(NotifyKind::alsoEmail)
                    .count();

            assertThat(emailed)
                    .as("email is a channel that gets spent; adding one here "
                            + "should be a deliberate decision, not a default")
                    .isLessThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Push is unchanged")
    class PushIsUnchanged {

        /**
         * Every kind still pushes. Email was added alongside push, never instead
         * of it, and this fails if a future change ever makes one exclude the
         * other.
         */
        @Test
        @DisplayName("every kind still reaches a phone")
        void everyKindPushes() {
            for (NotifyKind kind : NotifyKind.values()) {
                assertThat(kind.lockScreenBody("Instalment 2 of 45,000 is due"))
                        .as("%s has nothing to show on a phone", kind)
                        .isNotBlank();
            }
        }

        /**
         * Amounts still never reach a locked screen — except on a security
         * alert, which is useless if it does not say what happened.
         */
        @Test
        @DisplayName("a repayment reminder still hides the amount on the lock screen")
        void amountStaysOffTheLockScreen() {
            String full = "Instalment 2 of 45,000 is due in 3 days.";

            assertThat(NotifyKind.REPAYMENT_DUE.lockScreenBody(full))
                    .doesNotContain("45,000");
            assertThat(NotifyKind.SECURITY.lockScreenBody(full)).isEqualTo(full);
        }

        /**
         * A customer who switches repayment reminders off gets neither the push
         * nor the email. One preference, both channels — a switch that silences
         * half of what it names is worse than no switch.
         */
        @Test
        @DisplayName("repayment reminders can be turned off, and that covers email too")
        void oneSwitchCoversBoth() {
            assertThat(NotifyKind.REPAYMENT_DUE.optional()).isTrue();
            assertThat(NotifyKind.SECURITY.optional())
                    .as("a security alert is not optional in either channel")
                    .isFalse();
        }
    }
}
