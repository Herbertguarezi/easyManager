package com.guarezi.easymanager.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;

// sec. 10.2: rate limiting on the auth endpoints most attractive to brute
// force / abuse. Neither the request limit nor the window is specified
// in the docs — 10 requests/minute per (IP, endpoint) is this
// implementation's choice (see PROGRESS.md Fase 1 decisions log).
// Runs as a plain Filter (before DispatcherServlet), so a 429 is written
// directly here rather than thrown for GlobalExceptionHandler to catch —
// @ExceptionHandler only sees exceptions from within MVC's own handler
// execution, not from a Filter upstream of it. The ProblemDetail body
// shape is still built to match every other error response.
// Disabled by default in the automated test environment (see mvn test
// harness / PROGRESS.md decisions log) — with a shared in-memory counter
// and MockMvc always using the same fake remote address, the rest of the
// test suite makes far more than 10 calls/minute to these paths across
// its many unrelated test methods, which would otherwise start failing
// with 429s that have nothing to do with what those tests exercise. The
// filter's real logic is still covered directly (RateLimitingFilterTest)
// and end to end in a dedicated test that explicitly re-enables it in its
// own isolated Spring context.
@Component
@ConditionalOnProperty(name = "app.security.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int LIMIT = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Set<String> LIMITED_PATHS = Set.of(
            "/auth/register", "/auth/login", "/auth/refresh", "/auth/forgot-password", "/auth/reset-password");

    private final FixedWindowRateLimiter rateLimiter = new FixedWindowRateLimiter(LIMIT, WINDOW, Clock.systemUTC());
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!LIMITED_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = clientIp(request) + ":" + path;
        if (!rateLimiter.allow(key)) {
            respondTooManyRequests(response, path);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void respondTooManyRequests(HttpServletResponse response, String path) throws IOException {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
        problemDetail.setTitle("Too Many Requests");
        problemDetail.setDetail("Muitas requisições. Tente novamente em instantes.");
        problemDetail.setInstance(URI.create(path));

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problemDetail);
    }

    // Simplification: trusts the connection's own remote address, not
    // X-Forwarded-For — fine behind no proxy (this MVP's deployment target
    // today); would need explicit, careful trusted-proxy handling before
    // sitting behind a load balancer, otherwise a client could spoof that
    // header to bypass the limit entirely.
    private static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
