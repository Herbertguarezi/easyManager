package com.guarezi.easymanager.billing.infrastructure.security;

import com.guarezi.easymanager.billing.domain.Module;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Fase 1.5, Block C: opt-in module entitlement per endpoint (or per
// controller). No endpoint carries this yet — Fase 1.5 adds no HTTP
// endpoint of its own; this is infrastructure for Fase 2/3/7 to use. See
// RequiresModuleInterceptor for enforcement.
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresModule {
    Module value();

    // RF-AU23: execution endpoints (POST /ads/analysis/{storeId}, Fase 7)
    // must pass the composed check (canExecuteAnalysis — module + token
    // balance + confirmed seller), not just canUseModule. When true, the
    // interceptor reads a {storeId} path variable from the request.
    boolean execution() default false;
}
