package com.guarezi.easymanager.auth.infrastructure.security;

import com.guarezi.easymanager.auth.application.ports.out.AccessTokenIssuer;
import com.guarezi.easymanager.auth.application.ports.out.RequestIdentity;
import com.guarezi.easymanager.auth.domain.Role;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Block 0 (Fase 1.5 prerequisite): resolves sellerId/role from the access
// token and exposes it via RequestIdentityContext, but never blocks a
// request on its own — an absent/invalid token just means "no identity
// resolved", same as no header at all. /products/** stays public: nothing
// in that controller reads RequestIdentityContext yet, so this filter has
// no observable effect there (see PROGRESS.md decisions log for why
// blanket protection isn't added here).
class JwtAuthenticationFilterTest {

    private final AccessTokenIssuer accessTokenIssuer = mock(AccessTokenIssuer.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(accessTokenIssuer);
    private final FilterChain chain = mock(FilterChain.class);

    @Test
    void resolvesIdentityFromAValidBearerTokenAndAlwaysContinuesTheChain() throws Exception {
        UUID sellerId = UUID.randomUUID();
        RequestIdentity identity = new RequestIdentity(sellerId, Role.SELLER);
        when(accessTokenIssuer.verify("valid-token")).thenReturn(identity);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/products");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(RequestIdentityContext.get(request)).contains(identity);
    }

    @Test
    void leavesIdentityUnresolvedWhenNoAuthorizationHeaderIsPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/products");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(RequestIdentityContext.get(request)).isEmpty();
    }

    @Test
    void leavesIdentityUnresolvedWhenHeaderIsNotABearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/products");
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(RequestIdentityContext.get(request)).isEmpty();
    }

    @Test
    void doesNotRejectTheRequestWhenTheTokenIsInvalidOrExpired() throws Exception {
        when(accessTokenIssuer.verify("garbage")).thenThrow(new JwtException("expired"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/products");
        request.addHeader("Authorization", "Bearer garbage");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(RequestIdentityContext.get(request)).isEmpty();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
