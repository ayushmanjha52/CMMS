package com.plantdesk.security;

import com.plantdesk.config.PlantDeskProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Short-lived access tokens (15 min). Stateless by design: validating one costs a signature
 * check, not a database round trip. The price is that a deactivated user keeps access until
 * their current token expires — bounded at 15 minutes, because the refresh token (which is
 * server-side and checked against the user's active flag) is the only way to get another.
 */
@Service
public class JwtService {

    private static final String ISSUER = "plantdesk";
    private static final String CLAIM_TENANT = "tid";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NAME = "name";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Clock clock;

    public JwtService(PlantDeskProperties props, Clock clock) {
        // Throws WeakKeyException at startup if the secret is under 256 bits.
        this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.accessTtl = props.jwt().accessTtl();
        this.clock = clock;
    }

    public record AccessToken(String value, Instant expiresAt) {}

    public AccessToken issue(AuthenticatedUser user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(accessTtl);
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject(user.userId().toString())
                .claim(CLAIM_TENANT, user.tenantId().toString())
                .claim(CLAIM_ROLE, user.role().name())
                .claim(CLAIM_EMAIL, user.email())
                .claim(CLAIM_NAME, user.fullName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new AccessToken(token, expiresAt);
    }

    /** @throws JwtException if the signature, issuer, or expiry is invalid */
    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        try {
            return new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.get(CLAIM_TENANT, String.class)),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class)),
                    claims.get(CLAIM_EMAIL, String.class),
                    claims.get(CLAIM_NAME, String.class));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new JwtException("Malformed PlantDesk claims", e);
        }
    }
}
