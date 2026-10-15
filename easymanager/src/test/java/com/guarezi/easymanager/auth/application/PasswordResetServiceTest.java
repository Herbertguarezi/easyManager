package com.guarezi.easymanager.auth.application;

import com.guarezi.easymanager.auth.application.ports.out.BreachedPasswordChecker;
import com.guarezi.easymanager.auth.application.ports.out.PasswordResetTokenRepository;
import com.guarezi.easymanager.auth.application.ports.out.SellerRepository;
import com.guarezi.easymanager.auth.domain.InvalidPasswordResetTokenException;
import com.guarezi.easymanager.auth.domain.PasswordResetToken;
import com.guarezi.easymanager.auth.domain.Role;
import com.guarezi.easymanager.auth.domain.Seller;
import com.guarezi.easymanager.auth.domain.SellerStatus;
import com.guarezi.easymanager.shared.security.Sha256Hasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetServiceTest {

    @Mock
    private SellerRepository sellerRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private BreachedPasswordChecker breachedPasswordChecker;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(breachedPasswordChecker.isBreached(any())).thenReturn(false);
        PasswordPolicyValidator passwordPolicyValidator = new PasswordPolicyValidator(breachedPasswordChecker);
        service = new PasswordResetService(sellerRepository, passwordResetTokenRepository, passwordEncoder,
                passwordPolicyValidator, refreshTokenService);
        when(passwordResetTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void forgotPasswordIssuesATokenWhenEmailExists() {
        Seller seller = activeSeller();
        when(sellerRepository.findByEmail("seller@example.com")).thenReturn(Optional.of(seller));

        service.forgotPassword("seller@example.com");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getSellerId()).isEqualTo(seller.getId());
        assertThat(captor.getValue().getUsedAt()).isNull();
    }

    @Test
    void forgotPasswordDoesNothingObservableWhenEmailIsUnknown() {
        when(sellerRepository.findByEmail(any())).thenReturn(Optional.empty());

        service.forgotPassword("nobody@example.com");

        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void resetPasswordUpdatesHashMarksTokenUsedAndRevokesSessions() {
        Seller seller = activeSeller();
        String rawToken = "a-raw-reset-token";
        PasswordResetToken token = new PasswordResetToken(UUID.randomUUID(), seller.getId(),
                Sha256Hasher.hashHex(rawToken), Instant.now().plus(1, ChronoUnit.HOURS), null);
        when(passwordResetTokenRepository.findByTokenHash(Sha256Hasher.hashHex(rawToken))).thenReturn(Optional.of(token));
        when(sellerRepository.findById(seller.getId())).thenReturn(Optional.of(seller));

        service.resetPassword(rawToken, "a-brand-new-password");

        ArgumentCaptor<Seller> sellerCaptor = ArgumentCaptor.forClass(Seller.class);
        verify(sellerRepository).save(sellerCaptor.capture());
        assertThat(passwordEncoder.matches("a-brand-new-password", sellerCaptor.getValue().getPasswordHash())).isTrue();

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getUsedAt()).isNotNull();

        verify(refreshTokenService).revokeAllActiveTokensFor(seller.getId());
    }

    @Test
    void resetPasswordRejectsUnknownToken() {
        when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("unknown-token", "a-brand-new-password"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void resetPasswordRejectsExpiredToken() {
        String rawToken = "an-expired-reset-token";
        PasswordResetToken token = new PasswordResetToken(UUID.randomUUID(), UUID.randomUUID(),
                Sha256Hasher.hashHex(rawToken), Instant.now().minus(1, ChronoUnit.HOURS), null);
        when(passwordResetTokenRepository.findByTokenHash(Sha256Hasher.hashHex(rawToken))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword(rawToken, "a-brand-new-password"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void resetPasswordRejectsAlreadyUsedToken() {
        String rawToken = "a-used-reset-token";
        PasswordResetToken token = new PasswordResetToken(UUID.randomUUID(), UUID.randomUUID(),
                Sha256Hasher.hashHex(rawToken), Instant.now().plus(1, ChronoUnit.HOURS), Instant.now());
        when(passwordResetTokenRepository.findByTokenHash(Sha256Hasher.hashHex(rawToken))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword(rawToken, "a-brand-new-password"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    private Seller activeSeller() {
        return new Seller(UUID.randomUUID(), "seller@example.com", passwordEncoder.encode("old-password-value"),
                Role.SELLER, SellerStatus.ACTIVE, Instant.now(), null, null, Instant.now(), Instant.now(), 0, null, null);
    }
}
