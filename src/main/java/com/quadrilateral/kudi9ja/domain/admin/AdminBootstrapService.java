package com.quadrilateral.kudi9ja.domain.admin;

import com.quadrilateral.kudi9ja.config.Kudi9jaProperties;
import com.quadrilateral.kudi9ja.domain.audit.AuditCategory;
import com.quadrilateral.kudi9ja.domain.audit.AuditService;
import com.quadrilateral.kudi9ja.domain.user.User;
import com.quadrilateral.kudi9ja.domain.user.UserRepository;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
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
public class AdminBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

    private final AdminUserRepository admins;
    private final UserRepository users;
    private final AuditService audit;
    private final Kudi9jaProperties properties;

    public AdminBootstrapService(
            AdminUserRepository admins,
            UserRepository users,
            AuditService audit,
            Kudi9jaProperties properties) {
        this.admins = admins;
        this.users = users;
        this.audit = audit;
        this.properties = properties;
    }

    /**
     * Grants the panel to any named owner who already has an account.
     *
     * <p>The grant otherwise happens only as an account is opened, which leaves
     * an obvious gap: somebody added to the list after they signed up never
     * receives anything, and the only remedy is deleting a real account and
     * making it again. Running this at startup closes that.
     *
     * <p>Safe to run on every boot. It grants what is missing and does nothing
     * else — an owner who was later suspended or demoted through the panel is a
     * deliberate decision by a person, and a restart must not quietly undo it.
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<String> configured = properties.bootstrap().ownerEmails();
        if (configured == null) {
            return;
        }
        for (String email : configured) {
            if (email == null || email.isBlank()) {
                continue;
            }
            String address = email.trim();
            if (admins.existsByEmailIgnoreCase(address)) {
                continue;
            }
            users.findByEmailIgnoreCase(address).ifPresent(user -> {
                grant(user, "System (named owner, granted at startup)");
                log.info("Granted owner access to {}, who already had an account",
                        com.quadrilateral.kudi9ja.common.util.Masks.email(address));
            });
        }
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

        grant(user, "System (bootstrap owner from deployment configuration)");
        log.info("Granted bootstrap owner access to {}",
                com.quadrilateral.kudi9ja.common.util.Masks.email(user.getEmail()));
    }

    private void grant(User user, String by) {
        admins.save(AdminUser.grant(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                AdminRole.OWNER,
                by));

        audit.recordSystem(
                AuditCategory.TEAM,
                "Owner provisioned",
                user.getFullName() + " (" + user.getEmail() + ") was granted owner access as an "
                        + "owner named in deployment configuration.");
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
