package com.plantdesk.security;

import com.plantdesk.tenancy.TenantContext;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Turns a Bearer token into (1) a Spring Security principal and (2) the request's tenant.
 *
 * <p>The tenant comes from the signed {@code tid} claim and nowhere else. There is no code
 * path that reads a tenant id from a header, query parameter, or body — so a client that
 * sends {@code ?tenantId=<someone else's>} is sending a parameter nothing listens to.
 *
 * <p>Not a Spring bean on purpose: Spring Boot auto-registers every Filter bean with the
 * servlet container, which would run this a second time outside the security chain.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final ProblemWriter problemWriter;

    public JwtAuthenticationFilter(JwtService jwtService, ProblemWriter problemWriter) {
        this.jwtService = jwtService;
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }

        AuthenticatedUser user;
        try {
            user = jwtService.parse(header.substring(BEARER.length()));
        } catch (JwtException | IllegalArgumentException e) {
            problemWriter.write(response, HttpStatus.UNAUTHORIZED, "Access token invalid or expired");
            return;
        }

        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        TenantContext.set(user.toTenantScope());
        try {
            chain.doFilter(request, response);
        } finally {
            // Servlet threads are pooled. Forgetting this line is how request N+1 runs as
            // request N's tenant.
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
