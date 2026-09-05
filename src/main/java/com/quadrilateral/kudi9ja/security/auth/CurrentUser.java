package com.quadrilateral.kudi9ja.security.auth;

import com.quadrilateral.kudi9ja.common.error.ApiException;
import com.quadrilateral.kudi9ja.common.error.ErrorCode;
import com.quadrilateral.kudi9ja.domain.audit.AuditService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Who is calling. The one place that reads the security context, so no
 * controller or service has to.
 */
@Component
public class CurrentUser {

    /** The caller, or a refusal. Every authenticated endpoint goes through here. */
    public AuthPrincipal require() {
        return find().orElseThrow(() -> new ApiException(
                ErrorCode.UNAUTHENTICATED, "Sign in to continue."));
    }

    public UUID requireId() {
        return require().userId();
    }

    public Optional<AuthPrincipal> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof AuthPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    /** The caller as the audit log will name them. */
    public AuditService.Actor actor() {
        return find()
                .map(p -> new AuditService.Actor(p.userId(), p.fullName(), p.email()))
                .orElseGet(AuditService.Actor::system);
    }
}
