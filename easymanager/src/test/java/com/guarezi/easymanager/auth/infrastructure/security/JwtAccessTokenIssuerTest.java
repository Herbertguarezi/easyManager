package com.guarezi.easymanager.auth.infrastructure.security;

import com.guarezi.easymanager.auth.application.ports.out.RequestIdentity;
import com.guarezi.easymanager.auth.domain.Role;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAccessTokenIssuerTest {

    private static final String SECRET = "a-test-secret-that-is-at-least-32-bytes-long-for-hs256";

    private final JwtAccessTokenIssuer issuer = new JwtAccessTokenIssuer(SECRET);

    @Test
    void issuedTokenCarriesSellerIdAndRoleAndVerifies() {
        UUID sellerId = UUID.randomUUID();

        String token = issuer.issue(sellerId, Role.SELLER);
        RequestIdentity identity = issuer.verify(token);

        assertThat(identity.sellerId()).isEqualTo(sellerId);
        assertThat(identity.role()).isEqualTo(Role.SELLER);
    }

    @Test
    void rejectsATokenSignedWithADifferentKey() {
        JwtAccessTokenIssuer otherIssuer = new JwtAccessTokenIssuer("a-completely-different-secret-that-is-also-32-bytes-plus");
        String tokenFromOtherIssuer = otherIssuer.issue(UUID.randomUUID(), Role.SELLER);

        assertThatThrownBy(() -> issuer.verify(tokenFromOtherIssuer))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void rejectsATamperedToken() {
        String token = issuer.issue(UUID.randomUUID(), Role.SELLER);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> issuer.verify(tampered))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }
}
