package com.guarezi.easymanager.shared.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// Exercises the filter directly (no Spring context) — see PROGRESS.md
// Fase 1 decisions log for why the real bean is disabled in the rest of
// the automated test suite.
class RateLimitingFilterTest {

    @Test
    void allowsTenRequestsPerMinutePerIpAndPathThenReturns429() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 10; i++) {
            filter.doFilter(new MockHttpServletRequest("POST", "/auth/login"), new MockHttpServletResponse(), chain);
        }
        verify(chain, times(10)).doFilter(any(), any());

        MockHttpServletResponse eleventhResponse = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/auth/login"), eleventhResponse, chain);

        assertThat(eleventhResponse.getStatus()).isEqualTo(429);
        assertThat(eleventhResponse.getContentAsString()).contains("\"status\":429");
        verify(chain, times(10)).doFilter(any(), any());
    }

    @Test
    void tracksEachLimitedPathIndependently() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 10; i++) {
            filter.doFilter(new MockHttpServletRequest("POST", "/auth/login"), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse registerResponse = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/auth/register"), registerResponse, chain);

        assertThat(registerResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotLimitUnrelatedPaths() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 20; i++) {
            filter.doFilter(new MockHttpServletRequest("GET", "/products"), new MockHttpServletResponse(), chain);
        }

        verify(chain, times(20)).doFilter(any(), any());
    }
}
