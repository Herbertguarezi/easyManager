package com.guarezi.easymanager.auth.infrastructure.security;

import com.guarezi.easymanager.auth.application.ports.out.RequestIdentity;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

// Carries the identity JwtAuthenticationFilter resolved (or didn't) for
// the lifetime of one request, as a request attribute — no ThreadLocal,
// since a Servlet request/response pair is already the right scope and
// this avoids any risk of leaking an identity into a pooled thread's next
// request if cleanup were ever missed.
public final class RequestIdentityContext {

    private static final String ATTRIBUTE = RequestIdentityContext.class.getName() + ".IDENTITY";

    private RequestIdentityContext() {
    }

    static void set(HttpServletRequest request, RequestIdentity identity) {
        request.setAttribute(ATTRIBUTE, identity);
    }

    public static Optional<RequestIdentity> get(HttpServletRequest request) {
        return Optional.ofNullable((RequestIdentity) request.getAttribute(ATTRIBUTE));
    }
}
