package com.quadrilateral.kudi9ja.security.token;

import com.quadrilateral.kudi9ja.common.error.ApiException;
import com.quadrilateral.kudi9ja.common.error.ErrorCode;
import com.quadrilateral.kudi9ja.config.Kudi9jaProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and reads the signed tokens the app carries.
 *
 * <p>Access tokens are short-lived because the client locks itself after two
 * idle minutes and there is nothing to be gained by a long-lived bearer token
 * sitting on a phone. Refresh tokens are rotated: a used one is recorded and
 * refused a second time, so a stolen refresh token is good for one exchange at
 * most, and its reuse is a signal the session was compromised.
 *
 * <p>A session id travels in every token so a sign-out, a password change or an
 * admin freeze can revoke a live session rather than waiting for it to lapse.
 */
@Service
public class TokenService {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_SESSION = "sid";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_TIER = "tier";
    private static final String CLAIM_ADMIN = "admin";

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final Duration adminAccessTtl;

    public TokenService(Kudi9jaProperties properties) {
        Kudi9jaProperties.Jwt jwt = properties.jwt();
        byte[] secret = jwt.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "kudi9ja.jwt.secret must be at least 32 bytes. Set a real secret in deployment config.");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.issuer = jwt.issuer();
        this.accessTtl = jwt.accessTokenTtl();
        this.refreshTtl = jwt.refreshTokenTtl();
        this.adminAccessTtl = jwt.adminAccessTokenTtl();
    }

    /**
     * @param isAdmin whether the account also holds panel access. It is a hint
     *                for the client's navigation only — every admin endpoint
     *                re-checks the grant against the database on every request,
     *                because a claim in a token the client holds is exactly the
     *                sort of thing that gets forged.
     */
    public Issued issueAccess(UUID userId, String email, String tier, UUID sessionId, boolean isAdmin) {
        Duration ttl = isAdmin ? adminAccessTtl : accessTtl;
        Instant now = Instant.now();
        Instant expiry = now.plus(ttl);
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .id(UUID.randomUUID().toString())
                .claims(Map.of(
                        CLAIM_TYPE, TokenType.ACCESS.name(),
                        CLAIM_SESSION, sessionId.toString(),
                        CLAIM_EMAIL, email,
                        CLAIM_TIER, tier,
                        CLAIM_ADMIN, isAdmin))
                .signWith(key)
                .compact();
        return new Issued(token, expiry, ttl.toSeconds());
    }

    public Issued issueRefresh(UUID userId, UUID sessionId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(refreshTtl);
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .id(UUID.randomUUID().toString())
                .claims(Map.of(
                        CLAIM_TYPE, TokenType.REFRESH.name(),
                        CLAIM_SESSION, sessionId.toString()))
                .signWith(key)
                .compact();
        return new Issued(token, expiry, refreshTtl.toSeconds());
    }

    /** A token that carries a signup draft between the OTP and the account. */
    public Issued issueSignup(UUID draftId, String email, Duration ttl) {
        Instant now = Instant.now();
        Instant expiry = now.plus(ttl);
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(draftId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .id(UUID.randomUUID().toString())
                .claims(Map.of(
                        CLAIM_TYPE, TokenType.SIGNUP.name(),
                        CLAIM_EMAIL, email))
                .signWith(key)
                .compact();
        return new Issued(token, expiry, ttl.toSeconds());
    }

    /**
     * Reads a token, refusing anything that is not exactly the type asked for.
     *
     * @throws ApiException when the signature, the type or the expiry is wrong
     */
    public Parsed parse(String token, TokenType expected) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED, "Your session has expired. Sign in again.");
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(ErrorCode.TOKEN_INVALID, "That session token could not be read.");
        }

        String type = claims.get(CLAIM_TYPE, String.class);
        if (!expected.name().equals(type)) {
            throw new ApiException(ErrorCode.TOKEN_INVALID, "That token cannot be used here.");
        }

        String sessionClaim = claims.get(CLAIM_SESSION, String.class);
        return new Parsed(
                UUID.fromString(claims.getSubject()),
                sessionClaim == null ? null : UUID.fromString(sessionClaim),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_TIER, String.class),
                Boolean.TRUE.equals(claims.get(CLAIM_ADMIN, Boolean.class)),
                claims.getExpiration().toInstant(),
                claims.getId());
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }

    /** A token and when it stops working. */
    public record Issued(String token, Instant expiresAt, long expiresInSeconds) {
    }

    /** What a valid token said. */
    public record Parsed(
            UUID subject,
            UUID sessionId,
            String email,
            String tier,
            boolean admin,
            Instant expiresAt,
            String tokenId) {
    }
}
