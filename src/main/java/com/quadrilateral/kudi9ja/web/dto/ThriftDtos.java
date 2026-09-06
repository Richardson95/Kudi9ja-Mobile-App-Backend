package com.quadrilateral.kudi9ja.web.dto;

import com.quadrilateral.kudi9ja.domain.savings.AutoFrequency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Creating, joining and running a thrift circle. */
public final class ThriftDtos {

    private ThriftDtos() {
    }

    /**
     * Creating a circle.
     *
     * @param memberRefs the other members, by customer reference, email or
     *                   phone. <b>Every one must resolve to a real Kudi9ja
     *                   account</b> — a circle built around people who do not
     *                   exist collects nothing from them and pays the pot out
     *                   anyway.
     */
    public record CreateCircleRequest(
            @NotBlank(message = "Give the circle a name.")
            @Size(max = 120)
            String name,

            @NotNull(message = "How much does each member pay per round?")
            @DecimalMin(value = "0.01", message = "A contribution must be above zero.")
            BigDecimal contribution,

            @NotNull(message = "How often does the circle come round?")
            AutoFrequency frequency,

            @NotEmpty(message = "Add at least one other member.")
            List<String> memberRefs,

            String emoji,

            @NotBlank(message = "Your PIN is needed.")
            String pin) {
    }

    public record JoinCircleRequest(
            @NotBlank(message = "Enter the invite code.")
            String inviteCode) {
    }

    public record CirclePinRequest(
            @NotBlank(message = "Your PIN is needed.")
            String pin) {
    }

    public record MemberResponse(
            UUID id,
            UUID userId,
            String displayName,
            String initials,
            String customerRef,
            int position,
            boolean isMe,
            boolean hasCollected,
            boolean paidThisRound,
            Instant payoutDate) {
    }

    /** A circle, with everything the app needs to render it. */
    public record CircleResponse(
            UUID id,
            String name,
            String emoji,
            BigDecimal contribution,
            AutoFrequency frequency,
            String frequencyLabel,
            Instant startDate,
            int size,
            BigDecimal potSize,
            BigDecimal totalCommitment,
            int currentRound,
            boolean started,
            boolean complete,
            double progress,
            String inviteCode,
            boolean createdByMe,
            int myPosition,
            Instant myPayoutDate,
            boolean iHaveCollected,
            boolean iHavePaidThisRound,

            /**
             * Every round this member has actually paid into, oldest first.
             *
             * <p>Not the same as {@code iHavePaidThisRound}, which only answers
             * for the round now running. A circle that has moved on leaves no
             * other trace of what somebody did or did not pay, so without this
             * the app can show the current round and nothing behind it — and a
             * member who missed round two has no way to see that they did.
             */
            List<Integer> myRoundsPaid,

            int paidThisRound,
            Instant nextCollectionDate,
            MemberResponse currentCollector,
            List<MemberResponse> members) {
    }

    /** What happened when a round was collected. */
    public record RoundResponse(
            UUID circleId,
            int round,
            BigDecimal payout,
            String collectorRef,
            String collectorName,
            boolean youCollected,
            int nextRound,
            boolean complete,
            BigDecimal newBalance,
            String message) {
    }
}
