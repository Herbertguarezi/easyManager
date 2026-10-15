package com.guarezi.easymanager.auth.application;

import com.guarezi.easymanager.auth.application.ports.in.AuthTokens;
import com.guarezi.easymanager.auth.application.ports.out.SellerRepository;
import com.guarezi.easymanager.auth.domain.Role;
import com.guarezi.easymanager.auth.domain.Seller;
import com.guarezi.easymanager.auth.domain.SellerStatus;
import com.guarezi.easymanager.shared.exceptions.AuthenticationException;
import com.guarezi.easymanager.shared.security.TotpGenerator;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginServiceTest {

    @Mock
    private SellerRepository sellerRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private LoginService loginService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        loginService = new LoginService(sellerRepository, passwordEncoder, refreshTokenService);
    }

    @Test
    void issuesTokenPairForCorrectCredentials() {
        UUID sellerId = UUID.randomUUID();
        Seller seller = activeSeller(sellerId, "correct-password");
        when(sellerRepository.findByEmail("seller@example.com")).thenReturn(Optional.of(seller));
        AuthTokens expected = new AuthTokens("an-access-token", "a-refresh-token");
        when(refreshTokenService.issueNewPair(seller)).thenReturn(expected);

        AuthTokens tokens = loginService.login("seller@example.com", "correct-password", null);

        assertThat(tokens).isEqualTo(expected);
    }

    @Test
    void rejectsUnknownEmail() {
        when(sellerRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginService.login("nobody@example.com", "whatever-password", null))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void rejectsWrongPassword() {
        UUID sellerId = UUID.randomUUID();
        Seller seller = activeSeller(sellerId, "correct-password");
        when(sellerRepository.findByEmail("seller@example.com")).thenReturn(Optional.of(seller));

        assertThatThrownBy(() -> loginService.login("seller@example.com", "wrong-password", null))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void rejectsInactiveSeller() {
        UUID sellerId = UUID.randomUUID();
        Seller seller = activeSeller(sellerId, "correct-password");
        seller.setStatus(SellerStatus.INACTIVE);
        when(sellerRepository.findByEmail("seller@example.com")).thenReturn(Optional.of(seller));

        assertThatThrownBy(() -> loginService.login("seller@example.com", "correct-password", null))
                .isInstanceOf(AuthenticationException.class);
    }

    // Found in review: the unknown-email and inactive-account branches used
    // to skip the (deliberately slow) password comparison entirely, which
    // is a timing side-channel — a fast response reveals "this account
    // doesn't exist or is inactive" even though the HTTP response body is
    // identical to a wrong-password failure. These two tests prove the
    // comparison always runs, with a mocked PasswordEncoder standing in
    // for the real (slow) one so the assertion is deterministic instead
    // of measuring wall-clock time.
    @Test
    void comparesPasswordEvenWhenEmailIsUnknown() {
        PasswordEncoder mockEncoder = mock(PasswordEncoder.class);
        when(mockEncoder.matches(any(), any())).thenReturn(false);
        LoginService service = new LoginService(sellerRepository, mockEncoder, refreshTokenService);
        when(sellerRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("nobody@example.com", "whatever-password", null))
                .isInstanceOf(AuthenticationException.class);

        verify(mockEncoder).matches(eq("whatever-password"), any());
    }

    @Test
    void comparesPasswordEvenWhenSellerIsInactive() {
        PasswordEncoder mockEncoder = mock(PasswordEncoder.class);
        when(mockEncoder.matches(any(), any())).thenReturn(true);
        LoginService service = new LoginService(sellerRepository, mockEncoder, refreshTokenService);
        Seller inactiveSeller = activeSeller(UUID.randomUUID(), "whatever-password");
        inactiveSeller.setStatus(SellerStatus.INACTIVE);
        when(sellerRepository.findByEmail("seller@example.com")).thenReturn(Optional.of(inactiveSeller));

        assertThatThrownBy(() -> service.login("seller@example.com", "whatever-password", null))
                .isInstanceOf(AuthenticationException.class);

        verify(mockEncoder).matches(eq("whatever-password"), any());
    }

    // RF-AU15: progressive per-account throttling. Threshold and backoff
    // are an implementation decision (not specified in the docs) — see
    // PROGRESS.md Fase 1 decisions log: locks at the 5th consecutive
    // failure, doubling from 1 minute, capped at 60 minutes.
    @Test
    void locksAccountAfterFifthConsecutiveFailedAttempt() {
        UUID sellerId = UUID.randomUUID();
        Seller seller = activeSeller(sellerId, "correct-password");
        seller.setFailedLoginAttempts(4);
        when(sellerRepository.findByEmail("seller@example.com")).thenReturn(Optional.of(seller));

        assertThatThrownBy(() -> loginService.login("seller@example.com", "wrong-password", null))
                .isInstanceOf(AuthenticationException.class);

        ArgumentCaptor<Seller> captor = ArgumentCaptor.forClass(Seller.class);
        verify(sellerRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isEqualTo(5);
        assertThat(captor.getValue().getLockedUntil()).isAfter(Instant.now());
    }

    @Test
    void rejectsLoginWhileLockedEvenWithTheCorrectPassword() {
        UUID sellerId = UUID.randomUUID();
        Seller seller = activeSeller(sellerId, "correct-password");
        seller.setFailedLoginAttempts(5);
        seller.setLockedUntil(Instant.now().plus(1, ChronoUnit.MINUTES));
        when(sellerRepository.findByEmail("seller@example.com")).thenReturn(Optional.of(seller));

        assertThatThrownBy(() -> loginService.login("seller@example.com", "correct-password", null))
                .isInstanceOf(AuthenticationException.class);

        verify(refreshTokenService, never()).issueNewPair(any());
    }

    @Test
    void successfulLoginResetsFailedAttemptCounter() {
        UUID sellerId = UUID.randomUUID();
        Seller seller = activeSeller(sellerId, "correct-password");
        seller.setFailedLoginAttempts(3);
        when(sellerRepository.findByEmail("seller@example.com")).thenReturn(Optional.of(seller));
        when(refreshTokenService.issueNewPair(seller)).thenReturn(new AuthTokens("a-jwt-token", "a-refresh-token"));

        loginService.login("seller@example.com", "correct-password", null);

        ArgumentCaptor<Seller> captor = ArgumentCaptor.forClass(Seller.class);
        verify(sellerRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isZero();
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }

    @Test
    void allowsLoginAgainOnceLockoutHasExpired() {
        UUID sellerId = UUID.randomUUID();
        Seller seller = activeSeller(sellerId, "correct-password");
        seller.setFailedLoginAttempts(5);
        seller.setLockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(sellerRepository.findByEmail("seller@example.com")).thenReturn(Optional.of(seller));
        when(refreshTokenService.issueNewPair(seller)).thenReturn(new AuthTokens("a-jwt-token", "a-refresh-token"));

        AuthTokens tokens = loginService.login("seller@example.com", "correct-password", null);

        assertThat(tokens.accessToken()).isEqualTo("a-jwt-token");
    }

    // RF-AU17: MFA required for ADMIN, never for SELLER.
    @Test
    void doesNotRequireTotpForSeller() {
        UUID sellerId = UUID.randomUUID();
        Seller seller = activeSeller(sellerId, "correct-password");
        when(sellerRepository.findByEmail("seller@example.com")).thenReturn(Optional.of(seller));
        when(refreshTokenService.issueNewPair(seller)).thenReturn(new AuthTokens("a-jwt-token", "a-refresh-token"));

        AuthTokens tokens = loginService.login("seller@example.com", "correct-password", null);

        assertThat(tokens.accessToken()).isEqualTo("a-jwt-token");
    }

    @Test
    void requiresTotpForAdminEvenWithCorrectPassword() {
        UUID adminId = UUID.randomUUID();
        Seller admin = adminSeller(adminId, "correct-password", TotpGenerator.generateSecret());
        when(sellerRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> loginService.login("admin@example.com", "correct-password", null))
                .isInstanceOf(AuthenticationException.class);

        verify(refreshTokenService, never()).issueNewPair(any());
    }

    @Test
    void rejectsWrongTotpCodeForAdmin() {
        UUID adminId = UUID.randomUUID();
        String secret = TotpGenerator.generateSecret();
        Seller admin = adminSeller(adminId, "correct-password", secret);
        when(sellerRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        String wrongCode = TotpGenerator.currentCode(secret).equals("000000") ? "111111" : "000000";

        assertThatThrownBy(() -> loginService.login("admin@example.com", "correct-password", wrongCode))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void rejectsAdminLoginWhenMfaIsNotProvisionedYet() {
        UUID adminId = UUID.randomUUID();
        Seller admin = adminSeller(adminId, "correct-password", null);
        when(sellerRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        // Even a "code" is meaningless without a provisioned secret — must
        // fail closed, not treat "no secret" as "MFA optional".
        assertThatThrownBy(() -> loginService.login("admin@example.com", "correct-password", "123456"))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void acceptsAdminLoginWithCorrectTotpCode() {
        UUID adminId = UUID.randomUUID();
        String secret = TotpGenerator.generateSecret();
        Seller admin = adminSeller(adminId, "correct-password", secret);
        when(sellerRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(refreshTokenService.issueNewPair(admin)).thenReturn(new AuthTokens("an-admin-token", "a-refresh-token"));

        AuthTokens tokens = loginService.login("admin@example.com", "correct-password", TotpGenerator.currentCode(secret));

        assertThat(tokens.accessToken()).isEqualTo("an-admin-token");
    }

    private Seller activeSeller(UUID id, String rawPassword) {
        return new Seller(id, "seller@example.com", passwordEncoder.encode(rawPassword), Role.SELLER,
                SellerStatus.ACTIVE, Instant.now(), null, null, Instant.now(), Instant.now(), 0, null, null);
    }

    private Seller adminSeller(UUID id, String rawPassword, String mfaSecret) {
        Seller seller = new Seller(id, "admin@example.com", passwordEncoder.encode(rawPassword), Role.ADMIN,
                SellerStatus.ACTIVE, Instant.now(), null, null, Instant.now(), Instant.now(), 0, null, mfaSecret);
        return seller;
    }
}
