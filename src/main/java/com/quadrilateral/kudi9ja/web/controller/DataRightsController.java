package com.quadrilateral.kudi9ja.web.controller;

import com.quadrilateral.kudi9ja.domain.compliance.DataRightsService;
import com.quadrilateral.kudi9ja.domain.kyc.OtpService;
import com.quadrilateral.kudi9ja.security.auth.CurrentUser;
import com.quadrilateral.kudi9ja.web.dto.AuthDtos;
import com.quadrilateral.kudi9ja.web.dto.DataRightsDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A customer's rights over their own data.
 *
 * <p>These exist because the NDPA 2023 and the Privacy Policy shipped in the
 * app require them, not because anybody asked for the feature. Two are built
 * here: <b>access and portability</b>, answered by the export, and
 * <b>erasure</b>, answered by closing the account.
 *
 * <p>Both are on the customer's own account and neither is an admin action.
 * Nobody at Kudi9ja can close an account from the panel — the one route through
 * is this one, behind the customer's own password and a code sent to their
 * email — because closure applies retention rules that need the customer's
 * decision rather than a support agent's.
 *
 * <p>The Act allows thirty days to answer a subject access request. The export
 * answers on the spot: the data is all in one database, there is nothing to
 * gather, and a deadline would only be a delay.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Your data", description = "Export everything held about you, or close your account")
public class DataRightsController {

    private final DataRightsService dataRights;
    private final CurrentUser currentUser;

    public DataRightsController(DataRightsService dataRights, CurrentUser currentUser) {
        this.dataRights = dataRights;
        this.currentUser = currentUser;
    }

    /**
     * Everything Kudi9ja holds about the caller.
     *
     * <p>Sent as a download rather than a plain body, because portability means
     * the customer ends up with a <b>file</b> they can keep or hand to somebody
     * else. The filename carries the date so two exports are told apart.
     *
     * <p>No hashes and no full BVN or NIN. Those are masked here exactly as
     * they are everywhere else in this API: the customer supplied them and
     * already has them, and returning them in full would turn any live session
     * into a way to harvest an identity number.
     */
    @GetMapping(value = "/me/data-export", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Download everything held about you, as JSON")
    public ResponseEntity<DataRightsDtos.DataExport> export() {
        DataRightsDtos.DataExport document = dataRights.export(currentUser.requireId());

        String filename = "kudi9ja-data-export-" + LocalDate.now() + ".json";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.attachment()
                                .filename(filename)
                                .build()
                                .toString())
                // Nobody's shared cache should be holding a copy of this.
                .header(HttpHeaders.CACHE_CONTROL, "no-store, private")
                .body(document);
    }

    /**
     * What stands in the way of closing, before the customer commits to
     * anything.
     *
     * <p>The app shows this on the way in, so "your account cannot be closed"
     * is never the answer at the end of a flow. Each blocker is a sentence the
     * customer can act on.
     */
    @GetMapping("/me/closure")
    @Operation(summary = "Whether this account can be closed, and what is in the way")
    public DataRightsDtos.ClosureEligibility closureEligibility() {
        return dataRights.closureEligibility(currentUser.requireId());
    }

    /** Sends the code that a closure is confirmed with. */
    @PostMapping("/me/closure/code")
    @Operation(summary = "Send the code needed to close the account")
    public AuthDtos.OtpResponse startClosure() {
        OtpService.Issued issued = dataRights.startClosure(currentUser.requireId());
        return new AuthDtos.OtpResponse(
                issued.expiresAt(),
                issued.resendAfterSeconds(),
                issued.codeLength(),
                "We have sent a code to your email address. "
                        + "Closing your account cannot be undone by you afterwards.");
    }

    /**
     * Closes the account.
     *
     * <p>Two gates that prove different things: the code proves the person
     * holds the email address, and the password proves they are the account
     * holder rather than somebody who picked up an unlocked phone. A live
     * session on its own is not enough for something the customer cannot
     * reverse.
     *
     * <p>What happens is a <b>redaction, not a deletion</b>. The password,
     * passcode, PIN, security answer and preferences are destroyed at once; the
     * identity and transaction records are kept for the retention period the
     * AML rules require and erased when it ends. The response says which is
     * which and gives the date, because a "delete" that quietly means "keep
     * indefinitely" is the version of this that destroys trust.
     */
    @DeleteMapping("/account")
    @Operation(summary = "Close the account. Credentials are destroyed; records are retained.")
    public DataRightsDtos.ClosureResponse close(
            @Valid @RequestBody DataRightsDtos.CloseAccountRequest request) {
        return dataRights.close(currentUser.requireId(), request);
    }
}
