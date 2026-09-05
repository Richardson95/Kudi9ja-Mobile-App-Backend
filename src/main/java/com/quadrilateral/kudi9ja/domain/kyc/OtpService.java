package com.quadrilateral.kudi9ja.domain.kyc;

import com.quadrilateral.kudi9ja.common.error.ApiException;
import com.quadrilateral.kudi9ja.common.error.ErrorCode;
import com.quadrilateral.kudi9ja.common.util.Reference;
import com.quadrilateral.kudi9ja.config.Kudi9jaProperties;
import com.quadrilateral.kudi9ja.domain.settings.SettingsService;
import com.quadrilateral.kudi9ja.integration.email.Mailer;
import com.quadrilateral.kudi9ja.security.crypto.SecretHasher;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and checks the six-digit codes sent by email.
 *
 * <p>Five rules, and each of them closes a specific hole:
 *
 * <ul>
 *   <li><b>Never returned.</b> The code goes to the mailbox and nowhere else.
 *       The client's habit of showing it on screen does not survive here.
 *   <li><b>Single use.</b> Verifying consumes it, so a code read over someone's
 *       shoulder is worth nothing once used.
 *   <li><b>One live code per address.</b> Issuing retires the last, so an
 *       attacker cannot accumulate valid guesses by asking repeatedly.
 *   <li><b>Attempt-capped.</b> A code is burned after a handful of wrong
 *       guesses rather than left open for a million.
 *   <li><b>Rate-limited, with a resend cooldown</b> read from platform settings.
 * </ul>
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final OneTimeCodeRepository codes;
    private final SecretHasher hasher;
    private final Mailer mailer;
    private final SettingsService settings;
    private final Kudi9jaProperties properties;

    public OtpService(
            OneTimeCodeRepository codes,
            SecretHasher hasher,
            Mailer mailer,
            SettingsService settings,
            Kudi9jaProperties properties) {
        this.codes = codes;
        this.hasher = hasher;
        this.mailer = mailer;
        this.settings = settings;
        this.properties = properties;
    }

    /**
     * Issues a code and emails it.
     *
     * @return when the code expires and when another may be asked for. Never
     *         the code itself.
     */
    @Transactional
    public Issued issue(String email, OtpPurpose purpose, String recipientName, UUID userId) {
        String target = normalise(email);
        Instant now = Instant.now();

        int cooldownSeconds = settings.currentReadOnly().getOtpResendSeconds();
        codes.findFirstByTargetAndPurposeOrderByCreatedAtDesc(target, purpose).ifPresent(last -> {
            Instant nextAllowed = last.getCreatedAt().plusSeconds(cooldownSeconds);
            if (nextAllowed.isAfter(now)) {
                long wait = Duration.between(now, nextAllowed).toSeconds() + 1;
                throw new ApiException(
                        ErrorCode.OTP_COOLDOWN,
                        "Wait " + wait + " seconds before asking for another code.",
                        Map.of("retryAfterSeconds", wait));
            }
        });

        long recentlySent = codes.countByTargetAndPurposeAndCreatedAtAfter(
                target, purpose, now.minus(Duration.ofHours(1)));
        if (recentlySent >= properties.otp().maxPerHour()) {
            throw new ApiException(
                    ErrorCode.RATE_LIMITED,
                    "Too many codes have been requested for this address. Try again in an hour.");
        }

        // One live code at a time.
        codes.consumeOutstanding(target, purpose, now);

        String code = Reference.otp();
        Instant expiresAt = now.plus(properties.otp().ttl());
        codes.save(OneTimeCode.issue(target, purpose, hasher.hash(code), expiresAt, userId));

        mailer.send(
                target,
                subjectFor(purpose),
                "otp",
                Map.of(
                        "name", recipientName == null ? "there" : recipientName,
                        "code", code,
                        "purpose", descriptionFor(purpose),
                        "minutes", properties.otp().ttl().toMinutes()));

        log.info("Issued a {} code to {}", purpose, com.quadrilateral.kudi9ja.common.util.Masks.email(target));
        return new Issued(expiresAt, cooldownSeconds, properties.otp().length());
    }

    /**
     * Checks a code and consumes it.
     *
     * @throws ApiException when it is wrong, expired, already used, or has run
     *                      out of attempts
     */
    @Transactional
    public OneTimeCode verify(String email, OtpPurpose purpose, String code) {
        String target = normalise(email);
        Instant now = Instant.now();

        Optional<OneTimeCode> found =
                codes.findFirstByTargetAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(target, purpose);
        if (found.isEmpty()) {
            throw new ApiException(
                    ErrorCode.OTP_INVALID, "That code is not valid. Ask for a new one.");
        }

        OneTimeCode stored = found.get();
        if (!stored.isUsable(now)) {
            throw new ApiException(ErrorCode.OTP_EXPIRED, "That code has expired. Ask for a new one.");
        }

        if (stored.getAttempts() >= properties.otp().maxAttempts()) {
            stored.consume(now);
            codes.save(stored);
            throw new ApiException(
                    ErrorCode.OTP_INVALID, "Too many wrong tries. Ask for a new code.");
        }

        if (!hasher.matches(code == null ? "" : code.trim(), stored.getCodeHash())) {
            stored.setAttempts(stored.getAttempts() + 1);
            codes.save(stored);
            int left = properties.otp().maxAttempts() - stored.getAttempts();
            throw new ApiException(
                    ErrorCode.OTP_INVALID,
                    left > 0
                            ? "That code is not right. " + left + " " + (left == 1 ? "try" : "tries") + " left."
                            : "That code is not right. Ask for a new one.",
                    Map.of("attemptsLeft", Math.max(left, 0)));
        }

        stored.consume(now);
        return codes.save(stored);
    }

    @Transactional
    public int purgeExpired(Instant before) {
        return codes.deleteExpiredBefore(before);
    }

    private static String normalise(String email) {
        if (email == null || email.isBlank()) {
            throw ApiException.validation("An email address is needed.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String subjectFor(OtpPurpose purpose) {
        return switch (purpose) {
            case SIGNUP_EMAIL -> "Your Kudi9ja verification code";
            case PASSWORD_RESET -> "Reset your Kudi9ja password";
            case REACTIVATION -> "Reactivate your Kudi9ja account";
            case PAYOUT_CHANGE -> "Confirm your new payout account";
            case ACCOUNT_CLOSURE -> "Confirm closing your Kudi9ja account";
        };
    }

    private static String descriptionFor(OtpPurpose purpose) {
        return switch (purpose) {
            case SIGNUP_EMAIL -> "verify your email address";
            case PASSWORD_RESET -> "reset your password";
            case REACTIVATION -> "reactivate your account";
            case PAYOUT_CHANGE -> "change the account we pay you into";
            case ACCOUNT_CLOSURE -> "close your account";
        };
    }

    /**
     * What the client is told. Deliberately no code: the only place it appears
     * is the customer's inbox.
     */
    public record Issued(Instant expiresAt, int resendAfterSeconds, int codeLength) {
    }
}
