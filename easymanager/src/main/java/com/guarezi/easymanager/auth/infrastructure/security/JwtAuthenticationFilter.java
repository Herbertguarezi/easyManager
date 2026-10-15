package com.guarezi.easymanager.auth.infrastructure.security;

import com.guarezi.easymanager.auth.application.ports.out.AccessTokenIssuer;
import com.guarezi.easymanager.auth.application.ports.out.RequestIdentity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Fase 1.5, Block 0 prerequisite: resolves request identity (sellerId,
// role) from the access token, exposed via RequestIdentityContext.
//
// Deliberately permissive, not a gate: runs on every request, but an
// absent/malformed/expired/invalid token is never treated as a failure —
// it just leaves the identity unresolved, same as a request with no
// Authorization header at all. This is what lets the filter exist ahead
// of any endpoint that actually requires authentication: /products/** (no
// seller_id column until Fase 3) keeps working exactly as before, because
// nothing there reads RequestIdentityContext yet. Enforcement is opt-in,
// per endpoint — see RequiresRoleInterceptor — never a blanket
// "every request needs a valid token" rule enforced here.
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenIssuer accessTokenIssuer;

    public JwtAuthenticationFilter(AccessTokenIssuer accessTokenIssuer) {
        this.accessTokenIssuer = accessTokenIssuer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                RequestIdentity identity = accessTokenIssuer.verify(token);
                RequestIdentityContext.set(request, identity);
            } catch (RuntimeException invalidToken) {
                // No identity resolved; the request proceeds unauthenticated
                // (see class javadoc) — anything that actually requires it
                // fails on its own, later, opt-in.
            }
        }

        filterChain.doFilter(request, response);
    }
}
