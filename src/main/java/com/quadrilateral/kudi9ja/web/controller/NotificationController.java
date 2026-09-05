package com.quadrilateral.kudi9ja.web.controller;

import com.quadrilateral.kudi9ja.common.api.PageResponse;
import com.quadrilateral.kudi9ja.domain.notification.DevicePlatform;
import com.quadrilateral.kudi9ja.domain.notification.DeviceToken;
import com.quadrilateral.kudi9ja.domain.notification.Notification;
import com.quadrilateral.kudi9ja.domain.notification.NotificationService;
import com.quadrilateral.kudi9ja.domain.notification.NotifyKind;
import com.quadrilateral.kudi9ja.security.auth.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the customer has been told.
 *
 * <p>Every entry here was written by the server as something actually happened
 * — a plan matured, an auto-save was skipped for a short balance, a pay-in was
 * confirmed, a payout account changed. There is no endpoint for creating one,
 * because nothing outside this system has standing to tell a customer that
 * something happened to their money.
 *
 * <p>Clearing marks rather than deletes. Some of these are the customer's
 * record of a security event, and a feed that can be emptied is a feed an
 * intruder empties first.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "What the customer has been told, and when")
public class NotificationController {

    private final NotificationService notifications;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService notifications, CurrentUser currentUser) {
        this.notifications = notifications;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "This customer's notifications, newest first")
    public PageResponse<NotificationResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {

        return PageResponse.of(
                notifications.list(currentUser.requireId(), PageRequest.of(page, Math.min(size, 100))),
                NotificationResponse::from);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "How many are unread. What the badge shows.")
    public Map<String, Long> unreadCount() {
        return Map.of("unread", notifications.unreadCount(currentUser.requireId()));
    }

    @PostMapping("/read")
    @Operation(summary = "Mark everything read")
    public Map<String, Integer> markAllRead() {
        return Map.of("marked", notifications.markAllRead(currentUser.requireId()));
    }

    @DeleteMapping
    @Operation(summary = "Clear the feed")
    public Map<String, Integer> clearAll() {
        return Map.of("cleared", notifications.clearAll(currentUser.requireId()));
    }

    // ── Push: the devices a customer is reachable on ───────────────────────

    /**
     * Registers this phone for push.
     *
     * <p>Called on every sign-in rather than once at install. A Firebase token
     * rotates on its own — after a reinstall, a restore to a new handset, and
     * sometimes for no visible reason — so registering once would quietly stop
     * working and nobody would know until a customer said they had stopped
     * getting notifications.
     */
    @PostMapping("/devices")
    @Operation(summary = "Register this phone for push notifications")
    public Map<String, Object> registerDevice(@Valid @RequestBody RegisterDeviceRequest request) {
        DeviceToken device = notifications.registerDevice(
                currentUser.requireId(),
                request.token(),
                request.platform() == null ? DevicePlatform.ANDROID : request.platform(),
                request.deviceLabel());

        return Map.of(
                "registered", true,
                "deviceId", device.getId(),
                "message", "This phone will now be told when your money moves.");
    }

    /**
     * Drops this phone.
     *
     * <p>Sent on sign-out. A handset that is still registered after somebody
     * signs out keeps buzzing about an account it can no longer open, which is
     * a leak of exactly the thing push is careful about.
     */
    @DeleteMapping("/devices/{token}")
    @Operation(summary = "Stop sending push to this phone")
    public Map<String, Boolean> unregisterDevice(@PathVariable String token) {
        notifications.unregisterDevice(token);
        return Map.of("unregistered", true);
    }

    @GetMapping("/devices")
    @Operation(summary = "The phones this account is reachable on")
    public List<DeviceResponse> devices() {
        return notifications.devicesFor(currentUser.requireId()).stream()
                .map(DeviceResponse::from)
                .toList();
    }

    // ── Push: what the customer wants to hear about ────────────────────────

    /**
     * Which groups can be switched off, and which are on.
     *
     * <p>Security alerts and money movements come back marked non-optional
     * rather than being hidden, so the settings screen can show them switched
     * on and explain why they cannot be changed — which is more honest than a
     * list that quietly omits them.
     */
    @GetMapping("/preferences")
    @Operation(summary = "Notification groups, and which are switched on")
    public List<PreferenceResponse> preferences() {
        Set<NotifyKind> muted = notifications.mutedKinds(currentUser.requireId());
        return Arrays.stream(NotifyKind.values())
                .map(kind -> new PreferenceResponse(
                        kind,
                        kind.label(),
                        kind.optional(),
                        !muted.contains(kind),
                        kind.optional()
                                ? null
                                : "This is how you find out your money has moved, or that "
                                        + "somebody else is in your account."))
                .toList();
    }

    @PatchMapping("/preferences")
    @Operation(summary = "Switch a notification group on or off")
    public List<PreferenceResponse> setPreference(
            @Valid @RequestBody PreferenceRequest request) {

        notifications.setPreference(currentUser.requireId(), request.kind(), request.enabled());
        return preferences();
    }

    // ── Shapes ─────────────────────────────────────────────────────────────

    /**
     * @param token the Firebase registration token. Not a credential and grants
     *              nothing — it identifies an app installation, not a person.
     */
    public record RegisterDeviceRequest(
            @NotBlank(message = "A device token is needed.")
            @Size(max = 512)
            String token,

            DevicePlatform platform,

            @Size(max = 200)
            String deviceLabel) {
    }

    public record PreferenceRequest(
            @NotNull(message = "Say which group.")
            NotifyKind kind,

            @NotNull
            Boolean enabled) {
    }

    /**
     * @param canBeSwitchedOff false for the groups that carry money movements
     *                         and security alerts
     * @param whyNot           shown beside a group that cannot be changed,
     *                         rather than leaving a disabled switch unexplained
     */
    public record PreferenceResponse(
            NotifyKind kind,
            String label,
            boolean canBeSwitchedOff,
            boolean enabled,
            String whyNot) {
    }

    public record DeviceResponse(
            UUID id,
            DevicePlatform platform,
            String platformLabel,
            String deviceLabel,
            Instant registeredAt,
            Instant lastSeenAt) {

        public static DeviceResponse from(DeviceToken device) {
            // The token itself is never returned. It is of no use to the
            // customer and every copy of it is one more place it can leak.
            return new DeviceResponse(
                    device.getId(),
                    device.getPlatform(),
                    device.getPlatform().label(),
                    device.getDeviceLabel(),
                    device.getRegisteredAt(),
                    device.getLastSeenAt());
        }
    }

    /** One notification, as the app draws it. */
    public record NotificationResponse(
            UUID id,
            NotifyKind kind,
            String title,
            String body,
            Instant date,
            boolean read,
            BigDecimal amount) {

        public static NotificationResponse from(Notification notification) {
            return new NotificationResponse(
                    notification.getId(),
                    notification.getKind(),
                    notification.getTitle(),
                    notification.getBody(),
                    notification.getCreatedAt(),
                    notification.isRead(),
                    notification.getAmount());
        }
    }
}
