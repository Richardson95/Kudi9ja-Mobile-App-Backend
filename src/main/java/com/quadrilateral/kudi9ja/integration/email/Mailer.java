package com.quadrilateral.kudi9ja.integration.email;

import java.util.Map;

/**
 * How the server reaches a customer by email.
 *
 * <p>An interface rather than a concrete sender, so the OTP flow can be tested
 * without a mail server and so the transport can be swapped without touching
 * the flows that use it.
 */
public interface Mailer {

    /**
     * Sends a templated message.
     *
     * @param to      the recipient
     * @param subject the subject line
     * @param template the Thymeleaf template under {@code templates/email}
     * @param model   what the template renders from
     */
    void send(String to, String subject, String template, Map<String, Object> model);

    /** A plain-text message, for anything that does not warrant a template. */
    void sendPlain(String to, String subject, String body);
}
