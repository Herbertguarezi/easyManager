package com.guarezi.easymanager.auth.infrastructure.security;

import com.guarezi.easymanager.auth.domain.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Test-only controller used to exercise RequiresRoleInterceptor through
// the real dispatcher/filter chain.
@RestController
public class RequiresRoleTestController {

    @RequiresRole(Role.ADMIN)
    @GetMapping("/test/admin-only")
    public String adminOnly() {
        return "ok";
    }

    @GetMapping("/test/no-role-required")
    public String noRoleRequired() {
        return "ok";
    }
}
