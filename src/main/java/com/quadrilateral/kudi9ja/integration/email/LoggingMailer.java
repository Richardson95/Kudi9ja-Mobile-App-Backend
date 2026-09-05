package com.quadrilateral.kudi9ja.integration.email;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * A mailer for local development and tests.
 *
 * <p>It logs that a message would have gone out and to whom. It does
 * <b>not</b> log the body, because the body of the message this most often
 * carries is a one-time code, and a code in a log file is a code an operator
 * can read. Retrieve codes in tests from the repository, not from here.
 */
@Component
@ConditionalOnProperty(name = "kudi9ja.mail.enabled", havingValue = "false")
public class LoggingMailer implements Mailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailer.class);

    @Override
    public void send(String to, String subject, String template, Map<String, Object> model) {
        log.info("[mail disabled] would send '{}' ({}) to {}",
                subject, template, com.quadrilateral.kudi9ja.common.util.Masks.email(to));
    }

    @Override
    public void sendPlain(String to, String subject, String body) {
        log.info("[mail disabled] would send '{}' to {}",
                subject, com.quadrilateral.kudi9ja.common.util.Masks.email(to));
    }
}
