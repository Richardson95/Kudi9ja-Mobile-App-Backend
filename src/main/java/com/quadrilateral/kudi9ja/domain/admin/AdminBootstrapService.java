package com.quadrilateral.kudi9ja.domain.admin;

import com.quadrilateral.kudi9ja.config.Kudi9jaProperties;
import com.quadrilateral.kudi9ja.domain.audit.AuditCategory;
import com.quadrilateral.kudi9ja.domain.audit.AuditService;
import com.quadrilateral.kudi9ja.domain.user.User;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * How the first owner gets in.
 *
 * <p>The client makes the first account on a device the owner. That is a
 * development affordance and is not defensible on a server, where the first
 * account is whoever signs up first — a stranger.
 *
 * <p>So the first owner is <b>named in deployment configuration</b>. When an
 * account is opened with that email, and only then, the owner grant is created.
 * If the configuration names nobody, no owner is ever created automatically and
 * the panel has to be seeded deliberately by an operator.
 */
@Service
public class AdminBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

    private final AdminUserRepository admins;
    private final AuditService audit;
    private final Kudi9jaProperties properties;

    public AdminBootstrapService(
            AdminUserRepository admins, AuditService audit, Kudi9jaProperties properties) {
        this.admins = admins;
        this.audit = audit;
        this.properties = properties;
    }

    /**
     * Grants owner access if this account is the one configuration names, and
     * there is no owner yet.
     */
    @Transactional
    public void grantSeededOwnerIfMatching(User user) {
        String configured = properties.bootstrap().ownerEmail();
        if (configured == null || configured.isBlank()) {
            return;
        }
        if (!configured.trim().toLowerCase(Locale.ROOT)
                .equals(user.getEmail().toLowerCase(Locale.ROOT))) {
            return;
        }
        if (admins.countByRoleAndActiveTrue(AdminRole.OWNER) > 0) {
            log.warn("An owner already exists, so the configured bootstrap owner {} was not granted access.",
                    com.quadrilateral.kudi9ja.common.util.Masks.email(configured));
            return;
        }
        if (admins.existsByEmailIgnoreCase(user.getEmail())) {
            return;
        }

        AdminUser owner = AdminUser.grant(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                AdminRole.OWNER,
                "System (bootstrap owner from deployment configuration)");
        admins.save(owner);

        audit.recordSystem(
                AuditCategory.TEAM,
                "Owner provisioned",
                user.getFullName() + " (" + user.getEmail() + ") was granted owner access as the "
                        + "bootstrap owner named in deployment configuration.");
        log.info("Granted bootstrap owner access to {}", user.getEmail());
    }
}
