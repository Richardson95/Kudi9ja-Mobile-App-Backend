package com.quadrilateral.kudi9ja.web.controller;

import com.quadrilateral.kudi9ja.common.idempotency.IdempotencyService;
import com.quadrilateral.kudi9ja.domain.thrift.ThriftService;
import com.quadrilateral.kudi9ja.security.auth.CurrentUser;
import com.quadrilateral.kudi9ja.web.dto.ThriftDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thrift circles — ajo, esusu, adashe.
 *
 * <p>Everyone contributes each round and one member collects the pot; the turn
 * moves on until everybody has had one.
 *
 * <p>The thing the server has to do that the client could not is <b>resolve
 * every member to a real Kudi9ja account</b>. The Flutter app took typed names
 * and said on the field that they had to be existing customers, which is a
 * request rather than a rule. A circle built around people who do not exist
 * collects nothing from them and pays the pot out anyway — so members are given
 * here as customer references, each one is looked up, and a circle containing
 * anyone who is not a customer is refused outright.
 *
 * <p>Contributions are debited per member from their own wallet. Nobody's money
 * moves on somebody else's say-so: each member contributes with their own PIN.
 */
@RestController
@RequestMapping("/api/v1/circles")
@Tag(name = "Thrift", description = "Rotating savings circles")
public class ThriftController {

    private final ThriftService thrift;
    private final IdempotencyService idempotency;
    private final CurrentUser currentUser;

    public ThriftController(
            ThriftService thrift, IdempotencyService idempotency, CurrentUser currentUser) {
        this.thrift = thrift;
        this.idempotency = idempotency;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "The circles this customer is in")
    public List<ThriftDtos.CircleResponse> circles() {
        UUID userId = currentUser.requireId();
        return thrift.listFor(userId).stream()
                .map(circle -> thrift.toResponse(circle, userId))
                .toList();
    }

    @GetMapping("/{circleId}")
    @Operation(summary = "One circle, from this member's point of view")
    public ThriftDtos.CircleResponse circle(@PathVariable UUID circleId) {
        UUID userId = currentUser.requireId();
        return thrift.toResponse(thrift.get(userId, circleId), userId);
    }

    /**
     * Starts a circle.
     *
     * <p>Members are named by customer reference, and every one is resolved to
     * a real account before the circle exists. A reference that belongs to
     * nobody is refused with that reason rather than being silently dropped.
     */
    @PostMapping
    @Operation(summary = "Create a circle. Every member must be an existing Kudi9ja customer.")
    public ResponseEntity<ThriftDtos.CircleResponse> create(
            @Valid @RequestBody ThriftDtos.CreateCircleRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {

        UUID userId = currentUser.requireId();

        IdempotencyService.Result<ThriftDtos.CircleResponse> result = idempotency.execute(
                userId,
                key,
                "circle.create",
                new Object[] {request.name(), request.contribution(), request.frequency(), request.memberRefs()},
                ThriftDtos.CircleResponse.class,
                () -> thrift.toResponse(thrift.create(userId, request), userId));

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.value());
    }

    @PostMapping("/join")
    @Operation(summary = "Join a circle with its invite code")
    public ThriftDtos.CircleResponse join(@Valid @RequestBody ThriftDtos.JoinCircleRequest request) {
        UUID userId = currentUser.requireId();
        return thrift.toResponse(thrift.join(userId, request.inviteCode()), userId);
    }

    /**
     * Pays this member's contribution for the current round.
     *
     * <p>Debited from the caller's own wallet, on the caller's own PIN. One
     * member cannot contribute on another's behalf, however convenient that
     * would sometimes be.
     */
    @PostMapping("/{circleId}/contribute")
    @Operation(summary = "Pay this round's contribution")
    public ThriftDtos.CircleResponse contribute(
            @PathVariable UUID circleId,
            @Valid @RequestBody ThriftDtos.PinOnlyRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {

        UUID userId = currentUser.requireId();

        return idempotency.execute(
                userId,
                key,
                "circle.contribute",
                circleId,
                ThriftDtos.CircleResponse.class,
                () -> thrift.toResponse(thrift.contribute(userId, circleId, request.pin()), userId))
                .value();
    }

    /**
     * Pays the pot out to whoever collects this round, and moves the turn on.
     *
     * <p>Only runs once every member has contributed — paying a pot that is not
     * fully funded would be paying it out of somebody else's money.
     */
    @PostMapping("/{circleId}/payout")
    @Operation(summary = "Pay this round's pot to the member whose turn it is")
    public ThriftDtos.RoundResponse collectRound(@PathVariable UUID circleId) {
        return thrift.collectRound(currentUser.requireId(), circleId);
    }

    @PostMapping("/{circleId}/leave")
    @Operation(summary = "Leave a circle")
    public ResponseEntity<Void> leave(@PathVariable UUID circleId) {
        thrift.leave(currentUser.requireId(), circleId);
        return ResponseEntity.noContent().build();
    }
}
