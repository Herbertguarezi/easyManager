package com.guarezi.easymanager.auth.application;

import com.guarezi.easymanager.auth.application.ports.in.AuthTokens;
import com.guarezi.easymanager.auth.application.ports.out.AccessTokenIssuer;
import com.guarezi.easymanager.auth.application.ports.out.RefreshTokenRepository;
import com.guarezi.easymanager.auth.application.ports.out.SellerRepository;
import com.guarezi.easymanager.auth.domain.RefreshToken;
import com.guarezi.easymanager.auth.domain.Role;
import com.guarezi.easymanager.auth.domain.Seller;
import com.guarezi.easymanager.auth.domain.SellerStatus;
import com.guarezi.easymanager.shared.exceptions.AuthenticationException;
import com.guarezi.easymanager.shared.security.Sha256Hasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private SellerRepository sellerRepository;

    @Mock
    private AccessTokenIssuer accessTokenIssuer;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RefreshTokenService(refreshTokenRepository, sellerRepository, accessTokenIssuer);
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void issueNewPairPersistsOnlyTheHashAndReturnsTheRawToken() {
        Seller seller = activeSeller();
        when(accessTokenIssuer.issue(seller.getId(), Role.SELLER)).thenReturn("an-access-token");

        AuthTokens tokens = service.issueNewPair(seller);

        assertThat(tokens.accessToken()).isEqualTo("an-access-token");
        assertThat(tokens.refreshToken()).isNotBlank();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo(Sha256Hasher.hashHex(tokens.refreshToken()));
        assertThat(captor.getValue().getTokenHash()).isNotEqualTo(tokens.refreshToken());
        assertThat(captor.getValue().getRevokedAt()).isNull();
    }

    @Test
    void refreshRotatesTheTokenAndLinksReplacedBy() {
        Seller seller = activeSeller();
        String rawToken = "a-raw-refresh-token";
        RefreshToken existing = new RefreshToken(UUID.randomUUID(), seller.getId(), Sha256Hasher.hashHex(rawToken),
                Instant.now().plus(1, ChronoUnit.DAYS), Instant.now(), null, null);
        when(refreshTokenRepository.findByTokenHash(Sha256Hasher.hashHex(rawToken))).thenReturn(Optional.of(existing));
        when(sellerRepository.findById(seller.getId())).thenReturn(Optional.of(seller));
        when(accessTokenIssuer.issue(seller.getId(), Role.SELLER)).thenReturn("a-new-access-token");

        AuthTokens tokens = service.refresh(rawToken);

        assertThat(tokens.accessToken()).isEqualTo("a-new-access-token");
        assertThat(tokens.refreshToken()).isNotBlank().isNotEqualTo(rawToken);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
        RefreshToken newToken = captor.getAllValues().get(0);
        RefreshToken revokedOldToken = captor.getAllValues().get(1);

        assertThat(newToken.getTokenHash()).isEqualTo(Sha256Hasher.hashHex(tokens.refreshToken()));
        assertThat(revokedOldToken.getId()).isEqualTo(existing.getId());
        assertThat(revokedOldToken.getRevokedAt()).isNotNull();
        assertThat(revokedOldToken.getReplacedBy()).isEqualTo(newToken.getId());
    }

    @Test
    void refreshRejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("unknown-token"))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void refreshRejectsExpiredToken() {
        Seller seller = activeSeller();
        String rawToken = "an-expired-refresh-token";
        RefreshToken existing = new RefreshToken(UUID.randomUUID(), seller.getId(), Sha256Hasher.hashHex(rawToken),
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now(), null, null);
        when(refreshTokenRepository.findByTokenHash(Sha256Hasher.hashHex(rawToken))).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.refresh(rawToken))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void reusingARevokedTokenRevokesEveryActiveTokenForThatSeller() {
        UUID sellerId = UUID.randomUUID();
        String rawToken = "an-already-revoked-refresh-token";
        RefreshToken revokedToken = new RefreshToken(UUID.randomUUID(), sellerId, Sha256Hasher.hashHex(rawToken),
                Instant.now().plus(1, ChronoUnit.DAYS), Instant.now(), Instant.now().minus(1, ChronoUnit.MINUTES), UUID.randomUUID());
        when(refreshTokenRepository.findByTokenHash(Sha256Hasher.hashHex(rawToken))).thenReturn(Optional.of(revokedToken));

        RefreshToken otherActiveToken = new RefreshToken(UUID.randomUUID(), sellerId, "other-hash",
                Instant.now().plus(1, ChronoUnit.DAYS), Instant.now(), null, null);
        when(refreshTokenRepository.findActiveBySellerId(sellerId)).thenReturn(List.of(otherActiveToken));

        assertThatThrownBy(() -> service.refresh(rawToken))
                .isInstanceOf(AuthenticationException.class);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(otherActiveToken.getId());
        assertThat(captor.getValue().getRevokedAt()).isNotNull();

        verify(accessTokenIssuer, never()).issue(any(), any());
    }

    private Seller activeSeller() {
        return new Seller(UUID.randomUUID(), "seller@example.com", "hash", Role.SELLER, SellerStatus.ACTIVE,
                Instant.now(), null, null, Instant.now(), Instant.now(), 0, null, null);
    }
}
