package com.guarezi.easymanager.auth.infrastructure.security;

import com.guarezi.easymanager.auth.domain.Role;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Fase 1.5, Block 0: opt-in RBAC per endpoint (or per controller). No
// endpoint carries this yet — it's infrastructure for later phases to use
// (Fase 1.5's own subscription endpoints, then Fase 2/3). See
// RequiresRoleInterceptor for enforcement.
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {
    Role value();
}
