package com.plantdesk.auth;

import com.plantdesk.config.PlantDeskProperties;
import com.plantdesk.security.AuthenticatedUser;
import com.plantdesk.security.Roles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Token transport: the access token goes in the response body (the SPA keeps it in memory);
 * the refresh token goes in an HttpOnly, SameSite=Strict cookie scoped to /api/auth, so
 * JavaScript — including injected JavaScript — can never read it.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String REFRESH_COOKIE = "pd_refresh";

    private final AuthService authService;
    private final boolean secureCookie;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public AuthController(AuthService authService, PlantDeskProperties props) {
        this.authService = authService;
        this.secureCookie = props.cookie().secure();
        this.accessTtl = props.jwt().accessTtl();
        this.refreshTtl = props.jwt().refreshTtl();
    }

    public record LoginRequest(@NotBlank String plantCode, @NotBlank @Email String email, @NotBlank String password) {}

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, AuthService.SessionUser user) {}

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return respond(authService.login(request.plantCode(), request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token) {
        return respond(authService.refresh(token));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String token) {
        authService.logout(token);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .build();
    }

    @GetMapping("/me")
    @PreAuthorize(Roles.ANY)
    @Transactional(readOnly = true)
    public AuthService.SessionUser me(@AuthenticationPrincipal AuthenticatedUser me) {
        return authService.sessionUser(me.userId());
    }

    // Lifetimes come from configuration, not from re-reading the clock here: a second clock
    // read after issuing would report 899 s for a 900 s token and the client refreshes late.
    private ResponseEntity<TokenResponse> respond(AuthService.TokenPair pair) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(pair.refreshToken(), refreshTtl).toString())
                .body(new TokenResponse(pair.accessToken(), "Bearer", accessTtl.toSeconds(), pair.user()));
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }
}
