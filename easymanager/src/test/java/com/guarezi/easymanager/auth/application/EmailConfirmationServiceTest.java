package com.guarezi.easymanager.auth.application;

import com.guarezi.easymanager.auth.application.ports.out.EmailConfirmationTokenRepository;
import com.guarezi.easymanager.auth.application.ports.out.SellerRepository;
import com.guarezi.easymanager.auth.domain.EmailConfirmationToken;
import com.guarezi.easymanager.auth.domain.InvalidEmailConfirmationTokenException;
import com.guarezi.easymanager.auth.domain.Role;
import com.guarezi.easymanager.auth.domain.Seller;
import com.guarezi.easymanager.auth.domain.SellerStatus;
import com.guarezi.easymanager.shared.security.Sha256Hasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailConfirmationServiceTest {

    @Mock
    private EmailConfirmationTokenRepository tokenRepository;

    @Mock
    private SellerRepository sellerRepository;

    private EmailConfirmationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new EmailConfirmationService(tokenRepository, sellerRepository);
    }

    @Test
    void confirmsEmailAndActivatesSellerForAValidToken() {
        String rawToken = "raw-token-value";
        UUID sellerId = UUID.randomUUID();
        EmailConfirmationToken token = new EmailConfirmationToken(
                UUID.randomUUID(), sellerId, Sha256Hasher.hashHex(rawToken), Instant.now().plus(1, ChronoUnit.DAYS), null);
        Seller seller = new Seller(sellerId, "seller@example.com", "hash", Role.SELLER, SellerStatus.PENDING,
                null, null, null, Instant.now(), Instant.now(), 0, null, null);

        when(tokenRepository.findByTokenHash(Sha256Hasher.hashHex(rawToken))).thenReturn(Optional.of(token));
        when(sellerRepository.findById(sellerId)).thenReturn(Optional.of(seller));

        service.confirmEmail(rawToken);

        ArgumentCaptor<Seller> sellerCaptor = ArgumentCaptor.forClass(Seller.class);
        verify(sellerRepository).save(sellerCaptor.capture());
        assertThat(sellerCaptor.getValue().getStatus()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(sellerCaptor.getValue().getEmailConfirmedAt()).isNotNull();

        ArgumentCaptor<EmailConfirmationToken> tokenCaptor = ArgumentCaptor.forClass(EmailConfirmationToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getUsedAt()).isNotNull();
    }

    @Test
    void rejectsUnknownToken() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmEmail("unknown-token"))
                .isInstanceOf(InvalidEmailConfirmationTokenException.class);
    }

    @Test
    void rejectsExpiredToken() {
        String rawToken = "expired-token-value";
        EmailConfirmationToken token = new EmailConfirmationToken(
                UUID.randomUUID(), UUID.randomUUID(), Sha256Hasher.hashHex(rawToken),
                Instant.now().minus(1, ChronoUnit.DAYS), null);
        when(tokenRepository.findByTokenHash(Sha256Hasher.hashHex(rawToken))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.confirmEmail(rawToken))
                .isInstanceOf(InvalidEmailConfirmationTokenException.class);
    }

    @Test
    void rejectsAlreadyUsedToken() {
        String rawToken = "used-token-value";
        EmailConfirmationToken token = new EmailConfirmationToken(
                UUID.randomUUID(), UUID.randomUUID(), Sha256Hasher.hashHex(rawToken),
                Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.HOURS));
        when(tokenRepository.findByTokenHash(Sha256Hasher.hashHex(rawToken))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.confirmEmail(rawToken))
                .isInstanceOf(InvalidEmailConfirmationTokenException.class);
    }
}
