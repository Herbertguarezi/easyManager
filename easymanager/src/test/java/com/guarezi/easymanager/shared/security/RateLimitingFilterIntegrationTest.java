package com.guarezi.easymanager.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Explicitly re-enables the filter (disabled by default for the rest of
// the suite, see RateLimitingFilter/PROGRESS.md) — the differing property
// value gives this test its own Spring context, so it neither pollutes
// nor is polluted by the shared one every other @SpringBootTest uses.
@SpringBootTest(properties = "app.security.rate-limit.enabled=true")
@AutoConfigureMockMvc
class RateLimitingFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void realRequestsToAnAuthEndpointGet429AfterTenWithinAMinute() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"nobody@example.com\"}"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nobody@example.com\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
