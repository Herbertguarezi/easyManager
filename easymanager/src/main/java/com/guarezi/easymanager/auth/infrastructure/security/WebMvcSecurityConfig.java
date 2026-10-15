package com.guarezi.easymanager.auth.infrastructure.security;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class WebMvcSecurityConfig implements WebMvcConfigurer {

    private final RequiresRoleInterceptor requiresRoleInterceptor;

    public WebMvcSecurityConfig(RequiresRoleInterceptor requiresRoleInterceptor) {
        this.requiresRoleInterceptor = requiresRoleInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requiresRoleInterceptor);
    }
}
