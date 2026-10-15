package com.guarezi.easymanager.auth.application.ports.out;

import com.guarezi.easymanager.auth.domain.Role;

import java.util.UUID;

public interface AccessTokenIssuer {
    String issue(UUID sellerId, Role role);

    // Throws (unchecked, implementation-specific — e.g. a JJWT JwtException)
    // when the token is malformed, expired, or fails signature
    // verification. Block 0's JwtAuthenticationFilter treats any exception
    // here as "no identity resolved", never as a request-ending error on
    // its own — see the filter's own comment for why.
    RequestIdentity verify(String token);
}
