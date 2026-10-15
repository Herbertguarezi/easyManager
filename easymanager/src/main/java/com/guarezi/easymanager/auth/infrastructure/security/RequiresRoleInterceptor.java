package com.guarezi.easymanager.auth.infrastructure.security;

import com.guarezi.easymanager.auth.application.ports.out.RequestIdentity;
import com.guarezi.easymanager.auth.domain.Role;
import com.guarezi.easymanager.shared.exceptions.AuthenticationException;
import com.guarezi.easymanager.shared.exceptions.AuthorizationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

// Fase 1.5, Block 0: enforces @RequiresRole. Missing/invalid token (no
// identity in RequestIdentityContext — see JwtAuthenticationFilter) is
// 401, not 403 — the caller isn't known at all. A known caller with the
// wrong role is 403.
@Component
public class RequiresRoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequiresRole requiresRole = handlerMethod.getMethodAnnotation(RequiresRole.class);
        if (requiresRole == null) {
            requiresRole = handlerMethod.getBeanType().getAnnotation(RequiresRole.class);
        }
        if (requiresRole == null) {
            return true;
        }

        RequestIdentity identity = RequestIdentityContext.get(request)
                .orElseThrow(() -> new AuthenticationException("no identity resolved for " + request.getRequestURI()));

        Role required = requiresRole.value();
        if (identity.role() != required) {
            throw new AuthorizationException(
                    "role " + identity.role() + " insufficient, " + required + " required for " + request.getRequestURI());
        }

        return true;
    }
}
