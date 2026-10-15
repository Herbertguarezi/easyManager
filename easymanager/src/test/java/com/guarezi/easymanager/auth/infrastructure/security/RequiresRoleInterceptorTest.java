package com.guarezi.easymanager.auth.infrastructure.security;

import com.guarezi.easymanager.auth.application.ports.out.AccessTokenIssuer;
import com.guarezi.easymanager.auth.application.ports.out.RequestIdentity;
import com.guarezi.easymanager.auth.domain.Role;
import com.guarezi.easymanager.billing.application.ports.in.EntitlementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Fase 1.5, Block 0: @RequiresRole is opt-in per endpoint — an endpoint
// without it (noRoleRequired) is never gated by this mechanism, same as
// /products/** today (no endpoint there carries the annotation).
@WebMvcTest(controllers = RequiresRoleTestController.class)
class RequiresRoleInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccessTokenIssuer accessTokenIssuer;

    // Fase 1.5, Block C: RequiresModuleInterceptor is a global
    // WebMvcConfigurer-registered interceptor too, same reasoning as
    // AccessTokenIssuer above.
    @MockitoBean
    private EntitlementService entitlementService;

    @Test
    void rejectsWithUnauthorizedWhenNoIdentityWasResolved() throws Exception {
        mockMvc.perform(get("/test/admin-only"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWithForbiddenWhenRoleDoesNotMatch() throws Exception {
        when(accessTokenIssuer.verify("seller-token"))
                .thenReturn(new RequestIdentity(UUID.randomUUID(), Role.SELLER));

        mockMvc.perform(get("/test/admin-only").header("Authorization", "Bearer seller-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsWhenRoleMatches() throws Exception {
        when(accessTokenIssuer.verify("admin-token"))
                .thenReturn(new RequestIdentity(UUID.randomUUID(), Role.ADMIN));

        mockMvc.perform(get("/test/admin-only").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void endpointsWithoutTheAnnotationAreNeverGated() throws Exception {
        mockMvc.perform(get("/test/no-role-required"))
                .andExpect(status().isOk());
    }
}
