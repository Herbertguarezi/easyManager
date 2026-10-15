package com.guarezi.easymanager.auth.infrastructure.security;

import com.guarezi.easymanager.auth.application.ports.out.AccessTokenIssuer;
import com.guarezi.easymanager.auth.application.ports.out.RequestIdentity;
import com.guarezi.easymanager.auth.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

// HS256 with JWT_SECRET (see PROGRESS.md Fase 1 decisions log; resolves
// software-engineering.md sec. 13, Lacuna Conhecida #5). The algorithm is
// fixed here in code, never taken from the token's own header (sec.
// 4.2.1, "algorithm confusion") — JJWT's typed parser API enforces this
// by construction: verifyWith(SecretKey) only accepts a signature
// produced by an HMAC algorithm compatible with that specific key, it
// never lets the token's header dictate how it gets verified.
@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    // §4.1: access token lifetime is 15-30 minutes; the shorter bound is
    // used since a short-lived token is the whole point of the rotation
    // scheme the refresh token exists to support.
    private static final long ACCESS_TOKEN_TTL_MINUTES = 15;

    private final SecretKey key;

    public JwtAccessTokenIssuer(@Value("${app.security.keys.jwt-secret}") String jwtSecret) {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String issue(UUID sellerId, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(sellerId.toString())
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ACCESS_TOKEN_TTL_MINUTES, ChronoUnit.MINUTES)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    // Block 0 (Fase 1.5 prerequisite): promoted from a class-only method to
    // the AccessTokenIssuer port so JwtAuthenticationFilter can depend on
    // the port, not this concrete class. Returns RequestIdentity, not the
    // raw JJWT Claims — the port must not leak a JJWT-specific type to its
    // callers.
    @Override
    public RequestIdentity verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        UUID sellerId = UUID.fromString(claims.getSubject());
        Role role = Role.valueOf(claims.get("role", String.class));
        return new RequestIdentity(sellerId, role);
    }
}
