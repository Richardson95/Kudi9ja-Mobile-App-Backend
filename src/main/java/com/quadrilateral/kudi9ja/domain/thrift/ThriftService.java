package com.quadrilateral.kudi9ja.domain.thrift;

import com.quadrilateral.kudi9ja.common.error.ApiException;
import com.quadrilateral.kudi9ja.common.error.ErrorCode;
import com.quadrilateral.kudi9ja.common.util.Masks;
import com.quadrilateral.kudi9ja.common.util.Money;
import com.quadrilateral.kudi9ja.common.util.Reference;
import com.quadrilateral.kudi9ja.domain.notification.NotificationService;
import com.quadrilateral.kudi9ja.domain.notification.NotifyKind;
import com.quadrilateral.kudi9ja.domain.settings.PlatformSettings;
import com.quadrilateral.kudi9ja.domain.settings.SettingsService;
import com.quadrilateral.kudi9ja.domain.user.AuthService;
import com.quadrilateral.kudi9ja.domain.user.User;
import com.quadrilateral.kudi9ja.domain.user.UserRepository;
import com.quadrilateral.kudi9ja.domain.wallet.LedgerService;
import com.quadrilateral.kudi9ja.domain.wallet.TxKind;
import com.quadrilateral.kudi9ja.domain.wallet.WalletTransaction;
import com.quadrilateral.kudi9ja.web.dto.ThriftDtos;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ajo, esusu, adashe: a circle where everyone pays in each round and one person
 * collects the pot.
 *
 * <p>The two things the client cannot do are done here.
 *
 * <p><b>Members are real accounts.</b> Every reference is resolved before the
 * circle exists, and a circle containing anyone who is not a Kudi9ja customer
 * is refused outright. A circle of typed names collects nothing from the names
 * and pays the pot out regardless, which turns a savings product into a way to
 * lose money.
 *
 * <p><b>The pot is what was actually paid in.</b> A round pays out the
 * contributions it received, not the contributions it hoped for. Advancing a
 * round that nobody funded would hand a member money the circle does not hold.
 */
@Service
public class ThriftService {

    private static final Logger log = LoggerFactory.getLogger(ThriftService.class);

    private final ThriftCircleRepository circles;
    private final ThriftContributionRepository contributions;
    private final UserRepository users;
    private final LedgerService ledger;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final AuthService auth;

    public ThriftService(
            ThriftCircleRepository circles,
            ThriftContributionRepository contributions,
            UserRepository users,
            LedgerService ledger,
            SettingsService settings,
            NotificationService notifications,
            AuthService auth) {
        this.circles = circles;
        this.contributions = contributions;
        this.users = users;
        this.ledger = ledger;
        this.settings = settings;
        this.notifications = notifications;
        this.auth = auth;
    }

    // ── Create ─────────────────────────────────────────────────────────────

    /**
     * Creates a circle, having resolved every member to a real account.
     *
     * <p>The creator takes the first seat and therefore the first payout, which
     * is how an ajo usually runs: the person who organises it collects first,
     * and everybody can see that from the rotation before they join.
     */
    @Transactional
    public ThriftCircle create(UUID creatorId, ThriftDtos.CreateCircleRequest request) {
        settings.requireNotInMaintenance();
        settings.requireThriftEnabled();
        auth.verifyPin(creatorId, request.pin());

        PlatformSettings s = settings.currentReadOnly();
        User creator = requireUser(creatorId);

        BigDecimal contribution = Money.of(request.contribution());
        if (Money.lt(contribution, s.getMinCircleContribution())) {
            throw new ApiException(
                    ErrorCode.AMOUNT_TOO_SMALL,
                    "The smallest contribution is " + Money.naira(s.getMinCircleContribution()) + ".",
                    Map.of("minimum", s.getMinCircleContribution()));
        }

        // Resolve every member before anything is written. A circle that is
        // half-created because the fourth name did not exist is worse than one
        // that was refused.
        List<User> resolved = new ArrayList<>();
        resolved.add(creator);

        Set<String> seen = new LinkedHashSet<>();
        seen.add(creator.getCustomerRef());

        List<String> unknown = new ArrayList<>();
        for (String ref : request.memberRefs()) {
            if (ref == null || ref.isBlank()) {
                continue;
            }
            Optional<User> found = lookup(ref);
            if (found.isEmpty()) {
                unknown.add(ref.trim());
                continue;
            }
            User member = found.get();
            if (!member.getAccountStatus().canTransact()) {
                unknown.add(ref.trim());
                continue;
            }
            if (seen.add(member.getCustomerRef())) {
                resolved.add(member);
            }
        }

        if (!unknown.isEmpty()) {
            throw new ApiException(
                    ErrorCode.MEMBER_NOT_A_CUSTOMER,
                    unknown.size() == 1
                            ? "\"" + unknown.get(0) + "\" is not a Kudi9ja customer. "
                                    + "Everyone in a circle needs an account so their contributions "
                                    + "can actually be collected."
                            : unknown.size() + " of those people are not Kudi9ja customers. "
                                    + "Everyone in a circle needs an account.",
                    Map.of("unresolved", unknown));
        }

        if (resolved.size() < s.getMinCircleMembers()) {
            throw ApiException.validation(
                    "A circle needs at least " + s.getMinCircleMembers() + " people.");
        }
        if (resolved.size() > s.getMaxCircleMembers()) {
            throw ApiException.validation(
                    "A circle holds at most " + s.getMaxCircleMembers() + " people.");
        }

        ThriftCircle circle = new ThriftCircle();
        circle.setId(UUID.randomUUID());
        circle.setName(request.name().trim());
        circle.setContribution(contribution);
        circle.setFrequency(request.frequency());
        circle.setStartDate(Instant.now());
        circle.setCreatedBy(creatorId);
        circle.setCurrentRound(1);
        circle.setInviteCode(uniqueInviteCode());
        circle.setEmoji(request.emoji() == null || request.emoji().isBlank() ? "🤝" : request.emoji());
        circle.setStarted(true);

        for (int i = 0; i < resolved.size(); i++) {
            circle.getMembers().add(seatFor(circle, resolved.get(i), i + 1));
        }

        ThriftCircle saved = circles.save(circle);

        for (User member : resolved) {
            notifications.push(
                    member.getId(),
                    NotifyKind.THRIFT,
                    member.getId().equals(creatorId) ? "Circle created" : "You were added to a circle",
                    "\"" + saved.getName() + "\" is running with " + saved.size()
                            + " members at " + Money.naira(contribution) + " "
                            + request.frequency().adverb() + ". The pot is "
                            + Money.naira(saved.potSize()) + " each round. Your turn to collect is round "
                            + saved.memberFor(member.getId()).map(ThriftMember::getPosition).orElse(0)
                            + ".");
        }

        log.info("Created circle {} with {} members at {}",
                saved.getId(), saved.size(), contribution);
        return saved;
    }

    /**
     * Joins an open circle by its invite code.
     *
     * <p>Only before it starts. Joining a running circle would shift every
     * later member's payout position after they had already paid in against the
     * old one.
     */
    @Transactional
    public ThriftCircle join(UUID userId, String inviteCode) {
        settings.requireNotInMaintenance();
        settings.requireThriftEnabled();

        PlatformSettings s = settings.currentReadOnly();
        User user = requireUser(userId);

        ThriftCircle circle = circles.findByInviteCode(inviteCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("That circle"));

        if (circle.memberFor(userId).isPresent()) {
            throw new ApiException(ErrorCode.ALREADY_A_MEMBER, "You are already in that circle.");
        }
        if (circle.isStarted()) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    "That circle has already started. Joining now would move everyone else's turn.");
        }
        if (circle.size() >= s.getMaxCircleMembers()) {
            throw new ApiException(
                    ErrorCode.CIRCLE_FULL,
                    "That circle is full at " + s.getMaxCircleMembers() + " members.");
        }

        circle.getMembers().add(seatFor(circle, user, circle.size() + 1));
        ThriftCircle saved = circles.save(circle);

        notifications.push(
                circle.getCreatedBy(),
                NotifyKind.THRIFT,
                "Someone joined your circle",
                Masks.displayName(user.getFullName()) + " joined \"" + circle.getName()
                        + "\". It now has " + saved.size() + " members.");

        return saved;
    }

    // ── Contribute ─────────────────────────────────────────────────────────

    /** Pays this member's contribution into the round now being collected. */
    @Transactional
    public ThriftCircle contribute(UUID userId, UUID circleId, String pin) {
        settings.requireNotInMaintenance();
        auth.verifyPin(userId, pin);

        ThriftCircle circle = requireCircle(circleId);
        ThriftMember member = circle.memberFor(userId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.NOT_A_MEMBER, "You are not in that circle."));

        if (circle.isComplete()) {
            throw new ApiException(ErrorCode.CIRCLE_COMPLETE, "That circle has finished.");
        }
        if (contributions.existsByCircleIdAndUserIdAndRound(circleId, userId, circle.getCurrentRound())) {
            throw new ApiException(
                    ErrorCode.ALREADY_CONTRIBUTED,
                    "You have already paid into round " + circle.getCurrentRound() + ".");
        }

        WalletTransaction tx = ledger.debit(
                userId,
                circle.getContribution(),
                TxKind.SAVINGS_LOCK,
                LedgerService.Entry.of(
                                "Contribution to \"" + circle.getName() + "\" — round "
                                        + circle.getCurrentRound(),
                                circle.getName())
                        .related(LedgerService.Related.CIRCLE, circle.getId()));

        contributions.save(ThriftContribution.of(
                circleId, userId, circle.getCurrentRound(), circle.getContribution(), tx.getId()));

        long paid = contributions.countByCircleIdAndRound(circleId, circle.getCurrentRound());

        notifications.push(
                userId,
                NotifyKind.THRIFT,
                "Contribution received",
                "Round " + circle.getCurrentRound() + " of \"" + circle.getName()
                        + "\" is settled on your side. " + paid + " of " + circle.size()
                        + " members have paid.",
                circle.getContribution());

        // Tell whoever is collecting that the pot is filling.
        circle.currentCollector().ifPresent(collector -> {
            if (!collector.getUserId().equals(userId)) {
                notifications.push(
                        collector.getUserId(),
                        NotifyKind.THRIFT,
                        "Someone paid into your round",
                        Masks.displayName(member.getDisplayName()) + " paid into round "
                                + circle.getCurrentRound() + " of \"" + circle.getName() + "\". "
                                + paid + " of " + circle.size() + " in.");
            }
        });

        return circle;
    }

    /**
     * Closes the round: pays the collector what the round actually raised, and
     * moves the rotation on.
     *
     * <p>The payout is the sum of the contributions <b>received</b>, not the
     * theoretical pot. Paying out money that was never paid in would take it
     * from Kudi9ja rather than from the circle, and would reward a circle for
     * members who did not pay.
     */
    @Transactional
    public ThriftDtos.RoundResponse collectRound(UUID actingUserId, UUID circleId) {
        ThriftCircle circle = requireCircle(circleId);
        if (circle.memberFor(actingUserId).isEmpty()) {
            throw new ApiException(ErrorCode.NOT_A_MEMBER, "You are not in that circle.");
        }
        if (circle.isComplete()) {
            throw new ApiException(ErrorCode.CIRCLE_COMPLETE, "That circle has finished.");
        }

        int round = circle.getCurrentRound();
        ThriftMember collector = circle.currentCollector()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.CIRCLE_COMPLETE, "There is nobody left to collect."));

        List<ThriftContribution> paid = contributions.findByCircleIdAndRoundOrderByPaidAtAsc(circleId, round);
        BigDecimal pot = paid.stream()
                .map(ThriftContribution::getAmount)
                .reduce(Money.zero(), Money::add);

        if (Money.isZeroOrLess(pot)) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "Nobody has paid into round " + round + " yet.");
        }

        ledger.credit(
                collector.getUserId(),
                pot,
                TxKind.SAVINGS_RELEASE,
                LedgerService.Entry.of(
                                "Payout from \"" + circle.getName() + "\" — round " + round,
                                circle.getName())
                        .related(LedgerService.Related.CIRCLE, circle.getId()));

        circle.setCurrentRound(round + 1);
        boolean complete = circle.isComplete();
        if (complete) {
            circle.setCompletedAt(Instant.now());
        }
        circles.save(circle);

        notifications.push(
                collector.getUserId(),
                NotifyKind.THRIFT,
                "You collected the pot",
                Money.naira(pot) + " from \"" + circle.getName() + "\" is in your wallet"
                        + (paid.size() < circle.size()
                                ? ". " + (circle.size() - paid.size())
                                        + " member(s) had not paid this round, so the pot is smaller "
                                        + "than the full " + Money.naira(circle.potSize()) + "."
                                : "."),
                pot);

        if (complete) {
            circle.getMembers().forEach(member -> notifications.push(
                    member.getUserId(),
                    NotifyKind.THRIFT,
                    "Circle finished",
                    "\"" + circle.getName() + "\" has been round to everyone. Well done."));
        }

        return new ThriftDtos.RoundResponse(
                circle.getId(),
                round,
                pot,
                collector.getCustomerRef(),
                collector.getDisplayName(),
                collector.getUserId().equals(actingUserId),
                circle.getCurrentRound(),
                complete,
                ledger.balanceOf(actingUserId),
                complete
                        ? "That was the last round. The circle is finished."
                        : Money.naira(pot) + " went to " + collector.getDisplayName() + ".");
    }

    /**
     * Leaves a circle.
     *
     * <p>Only before it starts, and never if the customer has already put money
     * in. Walking out mid-rotation after collecting — or after others have paid
     * towards a turn that will now never come — is the failure mode every ajo
     * fears, and it is refused here rather than handled apologetically later.
     */
    @Transactional
    public void leave(UUID userId, UUID circleId) {
        ThriftCircle circle = requireCircle(circleId);
        ThriftMember member = circle.memberFor(userId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.NOT_A_MEMBER, "You are not in that circle."));

        boolean hasPaid = !contributions
                .findByCircleIdAndUserIdOrderByRoundAsc(circleId, userId).isEmpty();
        if (hasPaid || circle.getCurrentRound() > 1) {
            throw new ApiException(
                    ErrorCode.FORBIDDEN,
                    "You cannot leave a circle once it is running. Everyone else's turn depends on "
                            + "yours. Speak to the other members, or contact support.");
        }
        if (circle.getCreatedBy().equals(userId)) {
            throw new ApiException(
                    ErrorCode.FORBIDDEN,
                    "You created this circle, so you cannot leave it. Ask support to close it instead.");
        }

        circle.getMembers().remove(member);
        // Close the gap so positions stay 1..n and the rotation has no hole.
        List<ThriftMember> remaining = circle.getMembers();
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setPosition(i + 1);
        }
        circles.save(circle);
    }

    // ── Reads ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ThriftCircle> listFor(UUID userId) {
        return circles.findForMember(userId);
    }

    @Transactional(readOnly = true)
    public ThriftCircle get(UUID userId, UUID circleId) {
        ThriftCircle circle = requireCircle(circleId);
        if (circle.memberFor(userId).isEmpty()) {
            throw ApiException.notFound("That circle");
        }
        return circle;
    }

    @Transactional(readOnly = true)
    public BigDecimal committed(UUID userId) {
        return Money.of(contributions.committedToOpenCircles(userId));
    }

    @Transactional(readOnly = true)
    public long activeCircleCount(UUID userId) {
        return circles.countActiveForMember(userId);
    }

    /** Builds the response, filling in who has paid into the current round. */
    @Transactional(readOnly = true)
    public ThriftDtos.CircleResponse toResponse(ThriftCircle circle, UUID viewerId) {
        Set<UUID> paidThisRound = new LinkedHashSet<>();
        contributions.findByCircleIdAndRoundOrderByPaidAtAsc(circle.getId(), circle.getCurrentRound())
                .forEach(contribution -> paidThisRound.add(contribution.getUserId()));

        List<ThriftDtos.MemberResponse> members = circle.getMembers().stream()
                .map(member -> new ThriftDtos.MemberResponse(
                        member.getId(),
                        member.getUserId(),
                        member.getDisplayName(),
                        member.getInitials(),
                        member.getCustomerRef(),
                        member.getPosition(),
                        member.getUserId().equals(viewerId),
                        member.hasCollected(circle.getCurrentRound()),
                        paidThisRound.contains(member.getUserId()),
                        circle.dateForRound(member.getPosition())))
                .toList();

        int myPosition = circle.memberFor(viewerId).map(ThriftMember::getPosition).orElse(0);

        List<Integer> myRoundsPaid = contributions
                .findByCircleIdAndUserIdOrderByRoundAsc(circle.getId(), viewerId)
                .stream()
                .map(ThriftContribution::getRound)
                .toList();

        return new ThriftDtos.CircleResponse(
                circle.getId(),
                circle.getName(),
                circle.getEmoji(),
                circle.getContribution(),
                circle.getFrequency(),
                circle.getFrequency().label(),
                circle.getStartDate(),
                circle.size(),
                circle.potSize(),
                circle.totalCommitment(),
                circle.getCurrentRound(),
                circle.isStarted(),
                circle.isComplete(),
                circle.progress(),
                circle.getInviteCode(),
                circle.getCreatedBy().equals(viewerId),
                myPosition,
                myPosition > 0 ? circle.dateForRound(myPosition) : null,
                myPosition > 0 && circle.getCurrentRound() > myPosition,
                paidThisRound.contains(viewerId),
                myRoundsPaid,
                paidThisRound.size(),
                circle.nextCollectionDate(),
                members.stream()
                        .filter(m -> m.position() == circle.getCurrentRound())
                        .findFirst()
                        .orElse(null),
                members);
    }

    // ── Internals ──────────────────────────────────────────────────────────

    private ThriftCircle requireCircle(UUID circleId) {
        return circles.findById(circleId).orElseThrow(() -> ApiException.notFound("That circle"));
    }

    private User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("That account"));
    }

    private ThriftMember seatFor(ThriftCircle circle, User user, int position) {
        ThriftMember member = new ThriftMember();
        member.setId(UUID.randomUUID());
        member.setCircle(circle);
        member.setUserId(user.getId());
        member.setDisplayName(user.getFullName());
        member.setInitials(initials(user.getFullName()));
        member.setCustomerRef(user.getCustomerRef());
        member.setPosition(position);
        member.setJoinedAt(Instant.now());
        return member;
    }

    private String uniqueInviteCode() {
        for (int attempt = 0; attempt < 12; attempt++) {
            String candidate = Reference.inviteCode();
            if (!circles.existsByInviteCode(candidate)) {
                return candidate;
            }
        }
        throw new ApiException(ErrorCode.INTERNAL, "Could not create an invite code. Try again.");
    }

    private Optional<User> lookup(String query) {
        String wanted = query.trim();
        Optional<User> byRef = users.findByCustomerRef(wanted.toUpperCase(Locale.ROOT));
        if (byRef.isPresent()) {
            return byRef;
        }
        if (wanted.contains("@")) {
            return users.findByEmailIgnoreCase(wanted);
        }
        return users.findByPhone(wanted);
    }

    static String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "?";
        }
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        initials.append(Character.toUpperCase(parts[0].charAt(0)));
        if (parts.length > 1) {
            initials.append(Character.toUpperCase(parts[parts.length - 1].charAt(0)));
        }
        return initials.toString();
    }
}
