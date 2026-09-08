package com.quadrilateral.kudi9ja.domain.admin;

import com.quadrilateral.kudi9ja.config.Kudi9jaProperties;
import com.quadrilateral.kudi9ja.domain.audit.AuditCategory;
import com.quadrilateral.kudi9ja.domain.audit.AuditService;
import com.quadrilateral.kudi9ja.domain.user.User;
import java.util.List;
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
 * <p>So the owners are <b>named in deployment configuration</b>. When an
 * account is opened with one of those emails, and only then, the owner grant is
 * created. If the configuration names nobody, no owner is ever created
 * automatically and the panel has to be seeded deliberately by an operator.
 *
 * <p>More than one may be named, because a product with a single owner has a
 * single point of failure: one person loses their phone and nobody can reach
 * the panel to grant access to anybody else.
 *
 * <p>Each named email is granted independently. There is deliberately no "only
 * if there is no owner yet" rule — that would silently mean the second name in
 * the list never receives anything, which is the sort of failure nobody notices
 * until they need it.
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

    /** Grants owner access if this account is one configuration names. */
    @Transactional
    public void grantSeededOwnerIfMatching(User user) {
        if (!isNamedOwner(user.getEmail())) {
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

    /** Whether deployment configuration names this address as an owner. */
    private boolean isNamedOwner(String email) {
        List<String> configured = properties.bootstrap().ownerEmails();
        if (configured == null || configured.isEmpty() || email == null) {
            return false;
        }
        String wanted = email.trim().toLowerCase(Locale.ROOT);
        return configured.stream()
                .filter(e -> e != null && !e.isBlank())
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .anyMatch(wanted::equals);
    }
}
