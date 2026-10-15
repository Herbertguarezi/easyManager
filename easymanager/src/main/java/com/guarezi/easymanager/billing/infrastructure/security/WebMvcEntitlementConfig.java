package com.guarezi.easymanager.billing.infrastructure.security;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Separate from auth.infrastructure.security.WebMvcSecurityConfig on
// purpose — that one lives in `auth`, which `billing` already depends on
// (DefaultEntitlementService reads Seller via SellerRepository); the
// dependency must not go the other way.
@Component
public class WebMvcEntitlementConfig implements WebMvcConfigurer {

    private final RequiresModuleInterceptor requiresModuleInterceptor;

    public WebMvcEntitlementConfig(RequiresModuleInterceptor requiresModuleInterceptor) {
        this.requiresModuleInterceptor = requiresModuleInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requiresModuleInterceptor);
    }
}
