package com.guarezi.easymanager.billing.infrastructure.security;

import com.guarezi.easymanager.auth.application.ports.out.RequestIdentity;
import com.guarezi.easymanager.auth.infrastructure.security.RequestIdentityContext;
import com.guarezi.easymanager.billing.application.ports.in.EntitlementService;
import com.guarezi.easymanager.shared.exceptions.AuthenticationException;
import com.guarezi.easymanager.shared.exceptions.AuthorizationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.UUID;

// Fase 1.5, Block C: enforces @RequiresModule — RF-AU22 (403, not 404:
// the resource exists and is the seller's, it just isn't unlocked) and
// RF-AU23 (execution endpoints use the composed check). Missing/invalid
// identity (see JwtAuthenticationFilter) is 401, same reasoning as
// RequiresRoleInterceptor.
@Component
public class RequiresModuleInterceptor implements HandlerInterceptor {

    private final EntitlementService entitlementService;

    public RequiresModuleInterceptor(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequiresModule requiresModule = handlerMethod.getMethodAnnotation(RequiresModule.class);
        if (requiresModule == null) {
            requiresModule = handlerMethod.getBeanType().getAnnotation(RequiresModule.class);
        }
        if (requiresModule == null) {
            return true;
        }

        RequestIdentity identity = RequestIdentityContext.get(request)
                .orElseThrow(() -> new AuthenticationException("no identity resolved for " + request.getRequestURI()));

        boolean entitled = requiresModule.execution()
                ? entitlementService.canExecuteAnalysis(identity.sellerId(), storeIdPathVariable(request))
                : entitlementService.canUseModule(identity.sellerId(), requiresModule.value());

        if (!entitled) {
            throw new AuthorizationException("seller " + identity.sellerId() + " lacks entitlement for module "
                    + requiresModule.value() + " at " + request.getRequestURI());
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private UUID storeIdPathVariable(HttpServletRequest request) {
        Map<String, String> pathVariables =
                (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String storeId = pathVariables != null ? pathVariables.get("storeId") : null;
        if (storeId == null) {
            throw new IllegalStateException(
                    "@RequiresModule(execution = true) requires a {storeId} path variable on " + request.getRequestURI());
        }
        return UUID.fromString(storeId);
    }
}
