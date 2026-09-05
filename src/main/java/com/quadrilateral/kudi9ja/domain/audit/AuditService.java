package com.quadrilateral.kudi9ja.domain.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes the append-only record of every admin action.
 *
 * <p>Entries are written in their own transaction. If the action that produced
 * one is rolled back the entry still stands, which is the behaviour an audit
 * log needs: an attempt that failed halfway is exactly the thing an
 * investigation wants to see, and an audit that disappears with the thing it
 * was auditing is not an audit.
 */
@Service
public class AuditService {

    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEntry record(Actor actor, AuditCategory category, String action, String detail) {
        return record(actor, category, action, detail, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEntry record(
            Actor actor,
            AuditCategory category,
            String action,
            String detail,
            UUID subjectId,
            String subjectLabel) {

        AuditEntry entry = new AuditEntry();
        entry.setId(UUID.randomUUID());
        entry.setActor(actor == null ? "System" : actor.describe());
        entry.setActorId(actor == null ? null : actor.id());
        entry.setCategory(category);
        entry.setAction(action);
        entry.setDetail(truncate(detail, 4000));
        entry.setSubjectId(subjectId);
        entry.setSubjectLabel(truncate(subjectLabel, 200));
        entry.setOccurredAt(Instant.now());

        currentRequest().ifPresent(request -> {
            entry.setIpAddress(clientIp(request));
            entry.setUserAgent(truncate(request.getHeader("User-Agent"), 400));
        });

        return repository.save(entry);
    }

    /** Anything the system did on its own: a sweep, a seed, a scheduled job. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEntry recordSystem(AuditCategory category, String action, String detail) {
        return record(null, category, action, detail);
    }

    @Transactional(readOnly = true)
    public Page<AuditEntry> search(
            AuditCategory category, UUID subjectId, Instant from, Instant to, String query, Pageable pageable) {
        return repository.search(category, subjectId, from, to, query, pageable);
    }

    /** Who did something, as the log will name them. */
    public record Actor(UUID id, String name, String email) {

        public static Actor system() {
            return new Actor(null, "System", null);
        }

        public String describe() {
            if (email == null || email.isBlank()) {
                return name;
            }
            return name + " (" + email + ")";
        }
    }

    private static java.util.Optional<HttpServletRequest> currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            return java.util.Optional.of(servlet.getRequest());
        }
        return java.util.Optional.empty();
    }

    /**
     * The address the request actually came from. Behind a load balancer the
     * socket address is the balancer, so the forwarded header wins when the
     * deployment sets one.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return truncate((comma > 0 ? forwarded.substring(0, comma) : forwarded).trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
