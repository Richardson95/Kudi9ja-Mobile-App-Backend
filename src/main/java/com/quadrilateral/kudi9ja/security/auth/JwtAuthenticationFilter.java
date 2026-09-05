package com.quadrilateral.kudi9ja.security.auth;

import com.quadrilateral.kudi9ja.common.error.ApiException;
import com.quadrilateral.kudi9ja.domain.admin.AdminUserRepository;
import com.quadrilateral.kudi9ja.domain.user.User;
import com.quadrilateral.kudi9ja.domain.user.UserRepository;
import com.quadrilateral.kudi9ja.security.token.TokenService;
import com.quadrilateral.kudi9ja.security.token.TokenType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes who is calling, on every request.
 *
 * <p>Three things are checked and all three have to hold: the token verifies,
 * the session it names is still live, and the account it names may still sign
 * in. A revoked session or a closed account is refused immediately rather than
 * at the token's expiry.
 *
 * <p>Panel access is resolved here too, from the database rather than from the
 * token's claim, and granted as the {@code ROLE_ADMIN} authority. A client that
 * forges the claim gets nothing.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER = "Bearer ";

    private final TokenService tokens;
    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final AdminUserRepository admins;

    public JwtAuthenticationFilter(
            TokenService tokens,
            UserRepository users,
            UserSessionRepository sessions,
            AdminUserRepository admins) {
        this.tokens = tokens;
        this.users = users;
        this.sessions = sessions;
        this.admins = admins;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        Optional<String> bearer = bearerToken(request);
        if (bearer.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        try {
            TokenService.Parsed parsed = tokens.parse(bearer.get(), TokenType.ACCESS);
            authenticate(parsed, request);
        } catch (ApiException e) {
            // Leave the context empty and let the entry point answer. Throwing
            // here would bypass the handler that writes the standard error body.
            log.debug("Rejected token on {}: {}", request.getRequestURI(), e.getMessage());
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }

    private void authenticate(TokenService.Parsed parsed, HttpServletRequest request) {
        Instant now = Instant.now();

        if (parsed.sessionId() != null) {
            Optional<UserSession> session = sessions.findById(parsed.sessionId());
            if (session.isEmpty() || !session.get().isLive(now)) {
                log.debug("Token names a session that is no longer live");
                return;
            }
        }

        Optional<User> found = users.findById(parsed.subject());
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        if (!user.getAccountStatus().canSignIn()) {
            log.debug("Token names an account that may no longer sign in: {}", user.getAccountStatus());
            return;
        }

        // Panel access, from the database, on this request.
        boolean isAdmin = admins.findByEmailIgnoreCaseAndActiveTrue(user.getEmail()).isPresent();

        AuthPrincipal principal = new AuthPrincipal(
                user.getId(),
                parsed.sessionId(),
                user.getEmail(),
                user.getFullName(),
                user.getCustomerRef(),
                isAdmin);

        var authorities = isAdmin
                ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));

        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(request.getRequestURI());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            return Optional.empty();
        }
        String value = header.substring(BEARER.length()).trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }
}
