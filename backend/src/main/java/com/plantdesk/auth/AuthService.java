package com.plantdesk.auth;

import com.plantdesk.config.PlantDeskProperties;
import com.plantdesk.security.AuthenticatedUser;
import com.plantdesk.security.JwtService;
import com.plantdesk.security.LoginThrottle;
import com.plantdesk.security.Role;
import com.plantdesk.security.TooManyRequestsException;
import com.plantdesk.tenancy.SystemLookupDao;
import com.plantdesk.tenancy.TenantContext;
import com.plantdesk.tenancy.TenantScope;
import com.plantdesk.tenant.Tenant;
import com.plantdesk.tenant.TenantRepository;
import com.plantdesk.user.User;
import com.plantdesk.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Login, refresh-token rotation with reuse detection, logout.
 *
 * <p>None of these methods is {@code @Transactional}, deliberately. Each must first learn
 * the tenant (via a SECURITY DEFINER lookup), bind it, and only then open a transaction —
 * because the transaction is where the tenant filter and RLS variable are applied.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SystemLookupDao lookup;
    private final UserRepository users;
    private final TenantRepository tenants;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration refreshTtl;
    private final LoginThrottle throttle;
    // Compared against when the user does not exist, so "unknown email" and "wrong password"
    // take the same time. Otherwise response timing tells an attacker which emails exist.
    private final String dummyHash;

    public AuthService(SystemLookupDao lookup, UserRepository users, TenantRepository tenants,
                       RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       JwtService jwtService, TransactionTemplate tx, Clock clock, PlantDeskProperties props,
                       LoginThrottle throttle) {
        this.throttle = throttle;
        this.lookup = lookup;
        this.users = users;
        this.tenants = tenants;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tx = tx;
        this.clock = clock;
        this.refreshTtl = props.jwt().refreshTtl();
        this.dummyHash = passwordEncoder.encode("timing-equaliser");
    }

    public record SessionUser(UUID id, String email, String fullName, Role role, String plantCode, String plantName) {}

    public record TokenPair(String accessToken, Instant accessExpiresAt,
                            String refreshToken, Instant refreshExpiresAt, SessionUser user) {}

    public TokenPair login(String plantCode, String email, String password) {
        // Checked before any password work, so a throttled account costs no BCrypt time.
        long wait = throttle.accountBlockedFor(plantCode, email);
        if (wait > 0) {
            throw new TooManyRequestsException(wait);
        }
        var found = lookup.findUserForLogin(plantCode.trim(), email.trim());
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            throttle.recordFailure(plantCode, email);
            throw new BadCredentialsException("Plant code, email or password is incorrect");
        }
        var creds = found.get();
        if (!passwordEncoder.matches(password, creds.passwordHash()) || !creds.active()) {
            throttle.recordFailure(plantCode, email);
            throw new BadCredentialsException("Plant code, email or password is incorrect");
        }
        throttle.recordSuccess(plantCode, email);
        TenantScope scope = new TenantScope(creds.tenantId(), creds.userId(), Role.valueOf(creds.role()));
        return TenantContext.callAs(scope, () -> tx.execute(status -> {
            User user = users.findById(creds.userId()).orElseThrow();
            return issue(user, UUID.randomUUID());
        }));
    }

    public TokenPair refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadCredentialsException("No refresh token");
        }
        String hash = sha256(rawToken);
        UUID tenantId = lookup.findTenantOfRefreshToken(hash)
                .orElseThrow(() -> new BadCredentialsException("Refresh token not recognised"));

        // The rotation runs (and COMMITS) before we decide whether to throw. If reuse is
        // detected we revoke the family and must keep that revocation — throwing inside the
        // transaction would roll it back and leave the stolen family alive.
        Rotation result = TenantContext.callAs(TenantScope.system(tenantId),
                () -> tx.execute(status -> rotate(hash)));

        return switch (result.outcome()) {
            case ROTATED -> result.pair();
            case REUSE_DETECTED -> {
                log.warn("Refresh token reuse detected; family revoked (tenant={})", tenantId);
                throw new BadCredentialsException("Refresh token reuse detected. All sessions from that login were signed out.");
            }
            case EXPIRED -> throw new BadCredentialsException("Refresh token expired");
            case REVOKED -> throw new BadCredentialsException("Refresh token revoked");
            case USER_INACTIVE -> throw new BadCredentialsException("User is deactivated");
        };
    }

    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String hash = sha256(rawToken);
        lookup.findTenantOfRefreshToken(hash).ifPresent(tenantId ->
                TenantContext.runAs(TenantScope.system(tenantId), () -> tx.executeWithoutResult(status ->
                        refreshTokens.findForRotation(hash).ifPresent(t ->
                                refreshTokens.revokeFamily(t.getFamilyId(), clock.instant())))));
    }

    /** Must be called inside a tenant-scoped transaction. */
    public SessionUser sessionUser(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> new BadCredentialsException("User not found"));
        Tenant tenant = tenants.findById(user.getTenantId()).orElseThrow();
        return new SessionUser(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                tenant.getCode(), tenant.getName());
    }

    private enum Outcome { ROTATED, REUSE_DETECTED, EXPIRED, REVOKED, USER_INACTIVE }

    private record Rotation(Outcome outcome, TokenPair pair) {
        static Rotation of(Outcome o) { return new Rotation(o, null); }
    }

    private Rotation rotate(String hash) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findForRotation(hash)
                .orElseThrow(() -> new BadCredentialsException("Refresh token not recognised"));
        if (current.isRevoked()) {
            return Rotation.of(Outcome.REVOKED);
        }
        if (current.isUsed()) {
            // A rotated token came back. Either the legitimate client or an attacker holds a
            // copy; we cannot tell which, so neither keeps the session.
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            return Rotation.of(Outcome.REUSE_DETECTED);
        }
        if (current.isExpired(now)) {
            return Rotation.of(Outcome.EXPIRED);
        }
        User user = users.findById(current.getUserId()).orElse(null);
        if (user == null || !user.isActive()) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            return Rotation.of(Outcome.USER_INACTIVE);
        }
        TokenPair pair = issue(user, current.getFamilyId());
        RefreshToken successor = refreshTokens.findForRotation(sha256(pair.refreshToken())).orElseThrow();
        current.markRotated(now, successor.getId());
        return new Rotation(Outcome.ROTATED, pair);
    }

    private TokenPair issue(User user, UUID familyId) {
        Instant now = clock.instant();
        String raw = newRawToken();
        Instant refreshExpiresAt = now.plus(refreshTtl);
        refreshTokens.saveAndFlush(new RefreshToken(user.getId(), familyId, sha256(raw), now, refreshExpiresAt));

        var principal = new AuthenticatedUser(user.getId(), user.getTenantId(), user.getRole(),
                user.getEmail(), user.getFullName());
        var access = jwtService.issue(principal);
        return new TokenPair(access.value(), access.expiresAt(), raw, refreshExpiresAt, sessionUser(user.getId()));
    }

    private static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // SHA-256 rather than BCrypt: the input is 256 bits of randomness, not a human password,
    // so brute force is already infeasible and a fast hash allows an indexed lookup.
    static String sha256(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
