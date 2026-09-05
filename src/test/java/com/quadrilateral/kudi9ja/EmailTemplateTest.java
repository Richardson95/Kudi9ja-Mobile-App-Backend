package com.quadrilateral.kudi9ja;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * The email templates render.
 *
 * <p>This test exists because they did not. {@code SmtpMailer} asked Thymeleaf
 * for {@code email/otp.html}, the file had never been written, and the failure
 * was caught and logged — so sign-up stalled at step two with no code ever
 * arriving and nothing obviously broken. A missing template is invisible until
 * a customer is waiting on it.
 */
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("Email templates")
class EmailTemplateTest {

    @Autowired
    private TemplateEngine templates;

    private String renderOtp() {
        Context context = new Context();
        context.setVariables(Map.of(
                "name", "Chioma",
                "code", "482913",
                "purpose", "verify your email address",
                "minutes", 10L,
                "company", "Quadrilateral Technologies Limited",
                "rcNumber", "RC 1657731",
                "product", "Kudi9ja",
                "supportEmail", "support@kudi9ja.com",
                "supportPhone", "+234 800 5834 952"));
        return templates.process("email/otp", context);
    }

    @Test
    @DisplayName("the one-time code email renders, and carries the code")
    void otpRenders() {
        String html = renderOtp();

        assertThat(html).isNotBlank();
        assertThat(html).contains("482913");
        assertThat(html).contains("Chioma");
        assertThat(html).contains("verify your email address");
        assertThat(html).contains("10");
    }

    /**
     * The company, not the product. Every contract, receipt and message names
     * Quadrilateral Technologies Limited — and in an email the footer is also
     * how a customer tells a real one from a convincing forgery.
     */
    @Test
    @DisplayName("it names the company and the RC number")
    void namesTheCompany() {
        String html = renderOtp();

        assertThat(html).contains("Quadrilateral Technologies Limited");
        assertThat(html).contains("RC 1657731");
        assertThat(html).contains("support@kudi9ja.com");
    }

    /**
     * The single most useful sentence in the whole message. Every account
     * takeover that starts with a phone call ends with somebody reading a code
     * aloud.
     */
    @Test
    @DisplayName("it warns that nobody will ever ask for the code")
    void warnsAboutTheCode() {
        assertThat(renderOtp())
                .contains("will ever ask you")
                .contains("trying to take your money");
    }

    @Test
    @DisplayName("no unresolved Thymeleaf expressions survive rendering")
    void noUnresolvedPlaceholders() {
        String html = renderOtp();

        // A template that renders but leaves th:text attributes behind has not
        // really rendered; the client would show the fallback text instead.
        assertThat(html).doesNotContain("th:text");
        assertThat(html).doesNotContain("${");
    }

    @Test
    @DisplayName("the plain shell renders too")
    void plainRenders() {
        Context context = new Context();
        context.setVariables(Map.of(
                "body", "Your withdrawal has been approved.",
                "company", "Quadrilateral Technologies Limited",
                "rcNumber", "RC 1657731",
                "product", "Kudi9ja",
                "supportEmail", "support@kudi9ja.com",
                "supportPhone", "+234 800 5834 952"));

        String html = templates.process("email/plain", context);

        assertThat(html).contains("Your withdrawal has been approved.");
        assertThat(html).contains("RC 1657731");
    }
}
