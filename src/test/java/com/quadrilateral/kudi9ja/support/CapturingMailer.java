package com.quadrilateral.kudi9ja.support;

import com.quadrilateral.kudi9ja.integration.email.Mailer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A mailer that keeps what it was asked to send, so a test can read a one-time
 * code.
 *
 * <p>This exists because there is nowhere else to read one from, and that is
 * deliberate rather than an oversight. Codes are stored as hashes and never
 * logged: the Flutter client displayed them on screen so the flow could be
 * tested without a mailbox, and doing the same against a real backend would
 * hand every code to whoever asked. The seam that lets a test see one is the
 * mail transport, which is exactly where a real recipient would see it too.
 *
 * <p>Registered {@code @Primary} so it displaces the configured mailer for the
 * duration of a test, and nowhere near the main source tree.
 */
public class CapturingMailer implements Mailer {

    private final List<Sent> sent = new ArrayList<>();

    @Override
    public synchronized void send(String to, String subject, String template, Map<String, Object> model) {
        sent.add(new Sent(to, subject, template, model));
    }

    @Override
    public synchronized void sendPlain(String to, String subject, String body) {
        sent.add(new Sent(to, subject, "plain", Map.of("body", body)));
    }

    /** The most recent code sent to this address, if there is one. */
    public synchronized Optional<String> lastCodeFor(String email) {
        return sent.stream()
                .filter(message -> message.to().equalsIgnoreCase(email))
                .filter(message -> message.model().containsKey("code"))
                .reduce((first, second) -> second)
                .map(message -> String.valueOf(message.model().get("code")));
    }

    /** The code, or a failure that says which address was expected. */
    public String requireCodeFor(String email) {
        return lastCodeFor(email).orElseThrow(() -> new AssertionError(
                "No one-time code was sent to " + email + ". Sent so far: " + sent));
    }

    public synchronized List<Sent> all() {
        return List.copyOf(sent);
    }

    public synchronized void clear() {
        sent.clear();
    }

    public record Sent(String to, String subject, String template, Map<String, Object> model) {
    }

    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        public CapturingMailer capturingMailer() {
            return new CapturingMailer();
        }
    }
}
