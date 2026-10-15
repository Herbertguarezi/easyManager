package com.guarezi.easymanager.billing.infrastructure.security;

import com.guarezi.easymanager.auth.application.ports.out.AccessTokenIssuer;
import com.guarezi.easymanager.auth.application.ports.out.RequestIdentity;
import com.guarezi.easymanager.auth.domain.Role;
import com.guarezi.easymanager.billing.application.ports.in.EntitlementService;
import com.guarezi.easymanager.billing.domain.Module;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Fase 1.5, Block C: @RequiresModule is opt-in per endpoint, same pattern
// as @RequiresRole (Block 0) — no real endpoint carries it yet.
@WebMvcTest(controllers = RequiresModuleTestController.class)
class RequiresModuleInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccessTokenIssuer accessTokenIssuer;

    @MockitoBean
    private EntitlementService entitlementService;

    private final UUID sellerId = UUID.randomUUID();

    @Test
    void rejectsWithUnauthorizedWhenNoIdentityWasResolved() throws Exception {
        mockMvc.perform(get("/test/products-only"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWithForbiddenWhenTheModuleIsNotEntitled() throws Exception {
        authenticate("a-token");
        when(entitlementService.canUseModule(sellerId, Module.PRODUCTS)).thenReturn(false);

        mockMvc.perform(get("/test/products-only").header("Authorization", "Bearer a-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsWhenTheModuleIsEntitled() throws Exception {
        authenticate("a-token");
        when(entitlementService.canUseModule(sellerId, Module.PRODUCTS)).thenReturn(true);

        mockMvc.perform(get("/test/products-only").header("Authorization", "Bearer a-token"))
                .andExpect(status().isOk());
    }

    @Test
    void endpointsWithoutTheAnnotationAreNeverGated() throws Exception {
        mockMvc.perform(get("/test/no-module-required"))
                .andExpect(status().isOk());
    }

    @Test
    void executionEndpointsUseTheComposedCheckWithTheStoreIdPathVariable() throws Exception {
        // RF-AU23: execution endpoints use canExecuteAnalysis, not just
        // canUseModule.
        authenticate("a-token");
        UUID storeId = UUID.randomUUID();
        when(entitlementService.canExecuteAnalysis(sellerId, storeId)).thenReturn(true);

        mockMvc.perform(get("/test/ads-analysis/" + storeId).header("Authorization", "Bearer a-token"))
                .andExpect(status().isOk());
    }

    @Test
    void executionEndpointsRejectWhenTheComposedCheckFails() throws Exception {
        authenticate("a-token");
        UUID storeId = UUID.randomUUID();
        when(entitlementService.canExecuteAnalysis(sellerId, storeId)).thenReturn(false);

        mockMvc.perform(get("/test/ads-analysis/" + storeId).header("Authorization", "Bearer a-token"))
                .andExpect(status().isForbidden());
    }

    private void authenticate(String token) {
        when(accessTokenIssuer.verify(token)).thenReturn(new RequestIdentity(sellerId, Role.SELLER));
    }
}
