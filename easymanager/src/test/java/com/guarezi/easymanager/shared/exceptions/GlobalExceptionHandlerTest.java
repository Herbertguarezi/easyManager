package com.guarezi.easymanager.shared.exceptions;

import com.guarezi.easymanager.auth.application.ports.out.AccessTokenIssuer;
import com.guarezi.easymanager.billing.application.ports.in.EntitlementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {BoomTestController.class, DomainValidationTestController.class,
        AuthenticationTestController.class, CsrfValidationTestController.class, AuthorizationTestController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    // Fase 1.5, Block 0: JwtAuthenticationFilter is a global filter bean,
    // wired into every @WebMvcTest slice regardless of the controllers
    // list above — needs its dependency satisfied even though none of
    // these tests exercise auth.
    @MockitoBean
    private AccessTokenIssuer accessTokenIssuer;

    // Fase 1.5, Block C: RequiresModuleInterceptor is a global
    // WebMvcConfigurer-registered interceptor too, same reasoning.
    @MockitoBean
    private EntitlementService entitlementService;

    @Test
    void unhandledExceptionReturnsProblemDetailWithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.detail").value("Não foi possível processar a solicitação."))
                .andExpect(jsonPath("$.detail", not(containsString("hunter2"))))
                .andExpect(jsonPath("$.detail", not(containsString("RuntimeException"))))
                .andExpect(jsonPath("$.instance").value("/test/boom"));
    }

    @Test
    void domainValidationExceptionReturnsUnprocessableEntityWithItsOwnMessage() throws Exception {
        mockMvc.perform(get("/test/domain-validation"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Password must be at least 10 characters long"))
                .andExpect(jsonPath("$.instance").value("/test/domain-validation"));
    }

    @Test
    void authenticationExceptionReturnsUnauthorizedWithoutEchoingInternalDetail() throws Exception {
        mockMvc.perform(get("/test/authentication-failure"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail", not(containsString("seller@example.com"))))
                .andExpect(jsonPath("$.detail", not(containsString("wrong password"))))
                .andExpect(jsonPath("$.instance").value("/test/authentication-failure"));
    }

    @Test
    void csrfValidationExceptionReturnsForbiddenWithoutEchoingInternalDetail() throws Exception {
        mockMvc.perform(get("/test/csrf-failure"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail", not(containsString("abc123"))))
                .andExpect(jsonPath("$.detail", not(containsString("xyz789"))))
                .andExpect(jsonPath("$.instance").value("/test/csrf-failure"));
    }

    @Test
    void authorizationExceptionReturnsForbiddenWithoutEchoingInternalDetail() throws Exception {
        mockMvc.perform(get("/test/authorization-failure"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail", not(containsString("ADS_ANALYZER"))))
                .andExpect(jsonPath("$.instance").value("/test/authorization-failure"));
    }

    @Test
    void unmappedRouteReturnsNotFoundInsteadOfInternalServerError() throws Exception {
        mockMvc.perform(get("/test/this-route-does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }
}
