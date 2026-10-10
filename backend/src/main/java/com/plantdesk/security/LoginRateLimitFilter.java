package com.plantdesk.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Per-IP limit on POST /api/auth/login, applied before the request body is even parsed.
 * Not a Spring bean for the same reason as JwtAuthenticationFilter (avoid double registration).
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final LoginThrottle throttle;
    private final ProblemWriter problemWriter;

    public LoginRateLimitFilter(LoginThrottle throttle, ProblemWriter problemWriter) {
        this.throttle = throttle;
        this.problemWriter = problemWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/api/auth/login".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long wait = throttle.hitFromIp(request.getRemoteAddr());
        if (wait > 0) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(wait));
            problemWriter.write(response, HttpStatus.TOO_MANY_REQUESTS, TooManyRequestsException.message(wait));
            return;
        }
        chain.doFilter(request, response);
    }
}
