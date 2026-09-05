package com.quadrilateral.kudi9ja.web.controller;

import com.quadrilateral.kudi9ja.domain.kyc.OtpPurpose;
import com.quadrilateral.kudi9ja.domain.kyc.OtpService;
import com.quadrilateral.kudi9ja.domain.user.AuthService;
import com.quadrilateral.kudi9ja.security.auth.AuthPrincipal;
import com.quadrilateral.kudi9ja.security.auth.CurrentUser;
import com.quadrilateral.kudi9ja.security.auth.UserSession;
import com.quadrilateral.kudi9ja.web.dto.AuthDtos;
import com.quadrilateral.kudi9ja.web.support.RequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signing in, staying signed in, and changing the three things a customer
 * proves themselves with.
 *
 * <p>The three are not interchangeable and the app uses them for different
 * jobs. The <b>password</b> authenticates: it is what opens a session on a new
 * device. The <b>passcode</b> unlocks: the app locks itself after two idle
 * minutes and the six digits reopen it, which confirms the phone is in the
 * right hands and authenticates nothing on its own. The <b>PIN</b> authorises:
 * every movement of money asks for it, and it is verified here rather than
 * being taken on the client's word that it was typed.
 *
 * <p>None of the three is ever stored or returned in the clear, and no endpoint
 * on this controller answers with one.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Sessions, passcodes, PINs and passwords")
public class AuthController {

    private final AuthService auth;
    private final OtpService otps;
    private final CurrentUser currentUser;

    public AuthController(AuthService auth, OtpService otps, CurrentUser currentUser) {
        this.auth = auth;
        this.otps = otps;
        this.currentUser = currentUser;
    }

    @PostMapping("/signin")
    @Operation(summary = "Email and password. Opens a session.")
    public AuthDtos.SessionResponse signIn(
            @Valid @RequestBody AuthDtos.SignInRequest request,
            HttpServletRequest http) {

        // The body may name the device; the header is the fallback. Either way
        // it is a label for the customer's own security screen, never anything
        // a decision rests on.
        String device = request.device() != null && !request.device().isBlank()
                ? request.device().trim()
                : RequestContext.device(http);

        return AuthDtos.SessionResponse.from(
                auth.signIn(request.email(), request.password(), device, RequestContext.ipAddress(http)));
    }

    /**
     * Exchanges a refresh token for a new pair.
     *
     * <p>Refresh tokens rotate: the one presented is spent and a new one comes
     * back, so a stolen token is good for a single exchange rather than for as
     * long as the session lives.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a fresh pair")
    public AuthDtos.SessionResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return AuthDtos.SessionResponse.from(auth.refresh(request.refreshToken()));
    }

    @PostMapping("/signout")
    @Operation(summary = "End this session")
    public ResponseEntity<Void> signOut() {
        auth.signOut(currentUser.require().sessionId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Ends every session but this one.
     *
     * <p>The screen this sits behind is the one a customer reaches when they
     * think somebody else is in their account, so the session they are using
     * to ask is deliberately kept — signing them out along with the intruder
     * would lock them out of fixing it.
     */
    @PostMapping("/signout/all")
    @Operation(summary = "End every other session")
    public Map<String, Object> signOutEverywhere() {
        AuthPrincipal principal = currentUser.require();
        int ended = auth.signOutEverywhere(principal.userId(), "Signed out from another device");
        return Map.of(
                "sessionsEnded", ended,
                "message", ended == 0
                        ? "There were no other sessions."
                        : ended + (ended == 1 ? " other session was" : " other sessions were") + " ended.");
    }

    @GetMapping("/sessions")
    @Operation(summary = "Where this account is signed in")
    public List<AuthDtos.SessionSummary> sessions() {
        AuthPrincipal principal = currentUser.require();
        return auth.liveSessions(principal.userId()).stream()
                .map(session -> toSummary(session, principal))
                .toList();
    }

    // ── The passcode: unlocking the app ────────────────────────────────────

    /**
     * Checks the six-digit passcode.
     *
     * <p>Counted server-side, not on the device. Five failures lock the
     * account: a client-side counter is reset by reinstalling the app, which
     * makes it no counter at all.
     */
    @PostMapping("/passcode/verify")
    @Operation(summary = "Check the sign-in passcode. Counts failures.")
    public Map<String, Object> verifyPasscode(@Valid @RequestBody AuthDtos.PasscodeRequest request) {
        int remaining = auth.verifyPasscode(currentUser.requireId(), request.passcode());
        return Map.of("verified", true, "attemptsRemaining", remaining);
    }

    @PatchMapping("/passcode")
    @Operation(summary = "Change the sign-in passcode")
    public ResponseEntity<Void> changePasscode(@Valid @RequestBody AuthDtos.ChangePasscodeRequest request) {
        auth.changePasscode(currentUser.requireId(), request.currentPasscode(), request.newPasscode());
        return ResponseEntity.noContent().build();
    }

    // ── The PIN: authorising money ─────────────────────────────────────────

    /**
     * Checks the four-digit transaction PIN.
     *
     * <p>Provided so the app can confirm a PIN before assembling a transfer,
     * and not as a substitute for sending it: every money-moving endpoint takes
     * the PIN itself and verifies it there. A client assertion that the PIN was
     * entered is worth nothing.
     */
    @PostMapping("/pin/verify")
    @Operation(summary = "Check the transaction PIN")
    public Map<String, Object> verifyPin(@Valid @RequestBody AuthDtos.PinRequest request) {
        auth.verifyPin(currentUser.requireId(), request.pin());
        return Map.of("verified", true);
    }

    @PatchMapping("/pin")
    @Operation(summary = "Change the transaction PIN")
    public ResponseEntity<Void> changePin(@Valid @RequestBody AuthDtos.ChangePinRequest request) {
        auth.changePin(currentUser.requireId(), request.currentPin(), request.newPin());
        return ResponseEntity.noContent().build();
    }

    // ── The password: authenticating ───────────────────────────────────────

    /**
     * Changes the password and ends every other session.
     *
     * <p>A password change is what somebody does when they think their password
     * is known, so leaving the other sessions running would leave the intruder
     * exactly where they were.
     */
    @PatchMapping("/password")
    @Operation(summary = "Change the password. Ends every other session.")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        AuthPrincipal principal = currentUser.require();
        auth.changePassword(
                principal.userId(),
                request.currentPassword(),
                request.newPassword(),
                principal.sessionId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Starts a password reset.
     *
     * <p>Answers the same way whether or not the address belongs to an account.
     * A different answer for a known address turns this endpoint into a way of
     * asking which of a list of emails banks with Kudi9ja.
     */
    @PostMapping("/password/forgot")
    @Operation(summary = "Send a password reset code")
    public Map<String, String> forgotPassword(@Valid @RequestBody AuthDtos.ForgotPasswordRequest request) {
        auth.startPasswordReset(request.email());
        return Map.of("message",
                "If that email belongs to a Kudi9ja account, a reset code is on its way to it.");
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Set a new password using the emailed code")
    public Map<String, String> resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        auth.completePasswordReset(request.email(), request.code(), request.newPassword());
        return Map.of("message", "Your password has been changed. Sign in with it.");
    }

    // ── One-time codes ─────────────────────────────────────────────────────

    /**
     * Sends a code.
     *
     * <p>The code itself is never in the response. The Flutter client showed it
     * on screen so the flow could be tested without a mailbox; against a real
     * backend that would hand every code to whoever asked for one, so the only
     * thing that comes back here is when it expires and when another may be
     * requested.
     */
    @PostMapping("/otp/send")
    @Operation(summary = "Send a one-time code by email")
    public AuthDtos.OtpResponse sendCode(@Valid @RequestBody AuthDtos.OtpSendRequest request) {
        OtpService.Issued issued = otps.issue(request.email(), purposeOf(request.purpose()), null, null);
        return new AuthDtos.OtpResponse(
                issued.expiresAt(),
                issued.resendAfterSeconds(),
                issued.codeLength(),
                "If that email belongs to a Kudi9ja account, a code is on its way to it.");
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Check a one-time code")
    public Map<String, Object> verifyCode(@Valid @RequestBody AuthDtos.OtpVerifyRequest request) {
        otps.verify(request.email(), purposeOf(request.purpose()), request.code());
        return Map.of("verified", true);
    }

    private static OtpPurpose purposeOf(String raw) {
        try {
            return OtpPurpose.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw com.quadrilateral.kudi9ja.common.error.ApiException.validation(
                    "That is not something we send codes for.");
        }
    }

    private static AuthDtos.SessionSummary toSummary(UserSession session, AuthPrincipal principal) {
        return new AuthDtos.SessionSummary(
                session.getId(),
                session.getDeviceLabel(),
                session.getIpAddress(),
                session.getCreatedAt(),
                session.getLastSeenAt(),
                session.getId().equals(principal.sessionId()));
    }

    /**
     * Ends one named session.
     *
     * <p>Kept separate from signing out everywhere so a customer who recognises
     * one strange entry on their security screen can end that one alone.
     */
    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "End one session by id")
    public ResponseEntity<Void> endSession(@PathVariable java.util.UUID sessionId) {
        AuthPrincipal principal = currentUser.require();
        boolean theirs = auth.liveSessions(principal.userId()).stream()
                .anyMatch(session -> session.getId().equals(sessionId));
        if (!theirs) {
            throw com.quadrilateral.kudi9ja.common.error.ApiException.notFound("That session");
        }
        auth.signOut(sessionId);
        return ResponseEntity.noContent().build();
    }
}
