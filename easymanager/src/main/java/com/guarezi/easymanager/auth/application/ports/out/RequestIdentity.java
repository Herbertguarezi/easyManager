package com.guarezi.easymanager.auth.application.ports.out;

import com.guarezi.easymanager.auth.domain.Role;

import java.util.UUID;

// The result of resolving an access token into "who is making this
// request" — sellerId/role, nothing else. Deliberately independent of any
// JWT/JJWT type (see AccessTokenIssuer#verify) so callers (the auth
// filter, RBAC checks, and later EntitlementService callers) never depend
// on how the token happens to be implemented today.
public record RequestIdentity(UUID sellerId, Role role) {
}
