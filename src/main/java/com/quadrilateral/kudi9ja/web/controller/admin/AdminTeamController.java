package com.quadrilateral.kudi9ja.web.controller.admin;

import com.quadrilateral.kudi9ja.domain.admin.AdminTeamService;
import com.quadrilateral.kudi9ja.domain.admin.AdminUser;
import com.quadrilateral.kudi9ja.web.dto.AdminDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
 * Who has the panel.
 *
 * <p>Reading the team is open to every admin — knowing who can see your record
 * is not privileged information. Changing it is the owner's alone.
 *
 * <p>Three rules hold, and each closes a way the panel could be lost or given
 * away by accident:
 *
 * <ol>
 *   <li><b>A grant creates no account and no password.</b> It means: when
 *       somebody signs in with that email, the panel appears. Access may only
 *       be given to an email that <i>already belongs to an account</i>, so a
 *       mistyped address cannot hand the panel to a stranger, and there is
 *       never a second credential to steal.
 *   <li><b>Nobody can revoke their own access.</b> Not demote, not suspend, not
 *       remove — each would take away the permission needed to undo it. The
 *       team list marks the caller's own row as uneditable so the panel does
 *       not offer buttons that will be refused.
 *   <li><b>The last active owner cannot be demoted or suspended.</b> Rule two
 *       stops an owner locking themselves out; without this, two owners could
 *       still lock the company out by taking turns on each other.
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/admin/team")
@Tag(name = "Admin — team", description = "Granting, promoting and suspending panel access")
public class AdminTeamController {

    private final AdminTeamService team;

    public AdminTeamController(AdminTeamService team) {
        this.team = team;
    }

    @GetMapping
    @Operation(summary = "Everyone with panel access")
    public List<AdminDtos.TeamMemberResponse> list() {
        UUID me = team.me().getId();
        return team.list().stream()
                .map(admin -> AdminDtos.TeamMemberResponse.from(admin, me))
                .toList();
    }

    @GetMapping("/me")
    @Operation(summary = "The caller's own grant")
    public AdminDtos.TeamMemberResponse me() {
        AdminUser me = team.me();
        return AdminDtos.TeamMemberResponse.from(me, me.getId());
    }

    @GetMapping("/roles")
    @Operation(summary = "The four roles and what each one can do")
    public List<AdminDtos.RoleOption> roles() {
        // Reading the roles needs a grant like everything else here, and asking
        // for the caller's own is the cheapest way to require one.
        team.me();
        return AdminDtos.RoleOption.all();
    }

    @PostMapping
    @Operation(summary = "Give an existing Kudi9ja account the panel")
    public ResponseEntity<AdminDtos.TeamMemberResponse> grant(
            @Valid @RequestBody AdminDtos.GrantAccessRequest request) {

        UUID me = team.me().getId();
        AdminUser granted = team.grant(request.email(), request.role());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AdminDtos.TeamMemberResponse.from(granted, me));
    }

    @PatchMapping("/{adminId}/role")
    @Operation(summary = "Promote or demote. Never yourself, never the last owner.")
    public AdminDtos.TeamMemberResponse changeRole(
            @PathVariable UUID adminId,
            @Valid @RequestBody AdminDtos.ChangeRoleRequest request) {

        UUID me = team.me().getId();
        return AdminDtos.TeamMemberResponse.from(team.changeRole(adminId, request.role()), me);
    }

    /**
     * Suspends or restores a grant.
     *
     * <p>Suspending rather than removing keeps the record of what that person
     * did while they held the panel, which the audit log leans on.
     */
    @PatchMapping("/{adminId}/active")
    @Operation(summary = "Suspend or restore access")
    public AdminDtos.TeamMemberResponse setActive(
            @PathVariable UUID adminId,
            @Valid @RequestBody AdminDtos.SetActiveRequest request) {

        UUID me = team.me().getId();
        return AdminDtos.TeamMemberResponse.from(
                team.setActive(adminId, request.active(), request.reason()), me);
    }

    /**
     * Removes a grant.
     *
     * <p>The Kudi9ja account is untouched: the person keeps their wallet, their
     * savings and their loans. All they lose is the panel.
     */
    @DeleteMapping("/{adminId}")
    @Operation(summary = "Remove panel access. The customer account is unaffected.")
    public ResponseEntity<Void> revoke(@PathVariable UUID adminId) {
        team.revoke(adminId);
        return ResponseEntity.noContent().build();
    }
}
