package com.guarezi.easymanager.auth.application;

import com.guarezi.easymanager.auth.application.ports.in.AuthTokens;
import com.guarezi.easymanager.auth.application.ports.in.LoginUseCase;
import com.guarezi.easymanager.auth.application.ports.out.SellerRepository;
import com.guarezi.easymanager.auth.domain.Role;
import com.guarezi.easymanager.auth.domain.Seller;
import com.guarezi.easymanager.auth.domain.SellerStatus;
import com.guarezi.easymanager.shared.exceptions.AuthenticationException;
import com.guarezi.easymanager.shared.security.TotpGenerator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class LoginService implements LoginUseCase {

    // Found in review: comparing the password only when an active seller
    // was found made "unknown email" / "inactive account" return much
    // faster than "wrong password" — a timing side-channel that reveals
    // account existence/status despite the identical response body.
    // Comparing against this fixed dummy hash in those cases keeps the
    // (deliberately slow) BCrypt cost the same on every failure path.
    private static final String DUMMY_PASSWORD_HASH =
            new BCryptPasswordEncoder().encode("dummy-password-for-constant-time-login");

    // RF-AU15: progressive per-account throttling — no threshold/backoff
    // is specified in the docs, this is an implementation decision (see
    // PROGRESS.md Fase 1 decisions log). Locks starting at the 5th
    // consecutive failure, doubling the lockout each further failure,
    // capped at an hour.
    private static final int LOCKOUT_THRESHOLD = 5;
    private static final long MAX_LOCKOUT_MINUTES = 60;

    private final SellerRepository sellerRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public LoginService(SellerRepository sellerRepository, PasswordEncoder passwordEncoder,
                         RefreshTokenService refreshTokenService) {
        this.sellerRepository = sellerRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public AuthTokens login(String email, String rawPassword, String totpCode) {
        Optional<Seller> found = sellerRepository.findByEmail(email);

        String hashToCompare = found.map(Seller::getPasswordHash).orElse(DUMMY_PASSWORD_HASH);
        boolean passwordMatches = passwordEncoder.matches(rawPassword, hashToCompare);

        boolean inactive = found.map(seller -> seller.getStatus() == SellerStatus.INACTIVE).orElse(false);
        boolean locked = found.map(this::isCurrentlyLocked).orElse(false);
        boolean mfaSatisfied = found.map(seller -> isMfaSatisfied(seller, totpCode)).orElse(false);

        if (found.isEmpty() || inactive || locked || !passwordMatches || !mfaSatisfied) {
            found.ifPresent(this::recordFailedAttempt);
            throw new AuthenticationException("login failed for " + email);
        }

        Seller seller = found.get();
        seller.setFailedLoginAttempts(0);
        seller.setLockedUntil(null);
        sellerRepository.save(seller);

        return refreshTokenService.issueNewPair(seller);
    }

    // RF-AU17: MFA required for ADMIN only. An ADMIN with no secret
    // provisioned yet fails closed — there is no self-service admin
    // enrollment flow in this phase (see PROGRESS.md decisions log), so
    // "not provisioned" can only mean "not ready to log in", never
    // "MFA optional for this account".
    private boolean isMfaSatisfied(Seller seller, String totpCode) {
        if (seller.getRole() != Role.ADMIN) {
            return true;
        }
        return seller.getMfaSecret() != null
                && totpCode != null
                && TotpGenerator.verifyCode(seller.getMfaSecret(), totpCode);
    }

    private boolean isCurrentlyLocked(Seller seller) {
        return seller.getLockedUntil() != null && seller.getLockedUntil().isAfter(Instant.now());
    }

    private void recordFailedAttempt(Seller seller) {
        int attempts = seller.getFailedLoginAttempts() + 1;
        seller.setFailedLoginAttempts(attempts);
        if (attempts >= LOCKOUT_THRESHOLD) {
            long lockoutMinutes = Math.min(1L << (attempts - LOCKOUT_THRESHOLD), MAX_LOCKOUT_MINUTES);
            seller.setLockedUntil(Instant.now().plus(lockoutMinutes, ChronoUnit.MINUTES));
        }
        sellerRepository.save(seller);
    }
}
