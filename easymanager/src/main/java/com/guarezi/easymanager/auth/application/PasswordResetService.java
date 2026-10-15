package com.guarezi.easymanager.auth.application;

import com.guarezi.easymanager.auth.application.ports.in.ForgotPasswordUseCase;
import com.guarezi.easymanager.auth.application.ports.in.ResetPasswordUseCase;
import com.guarezi.easymanager.auth.application.ports.out.PasswordResetTokenRepository;
import com.guarezi.easymanager.auth.application.ports.out.SellerRepository;
import com.guarezi.easymanager.auth.domain.InvalidPasswordResetTokenException;
import com.guarezi.easymanager.auth.domain.PasswordResetToken;
import com.guarezi.easymanager.auth.domain.Seller;
import com.guarezi.easymanager.auth.domain.SellerStatus;
import com.guarezi.easymanager.shared.security.Sha256Hasher;
import com.guarezi.easymanager.shared.security.SecureTokenGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

// RF-AU16 (forgot-password) + revoking sessions on reset (see
// PROGRESS.md Fase 1 decisions log for the token TTL, not specified in
// the docs).
@Service
public class PasswordResetService implements ForgotPasswordUseCase, ResetPasswordUseCase {

    private static final int RESET_TOKEN_TTL_HOURS = 1;

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final SellerRepository sellerRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final RefreshTokenService refreshTokenService;

    public PasswordResetService(SellerRepository sellerRepository,
                                 PasswordResetTokenRepository passwordResetTokenRepository,
                                 PasswordEncoder passwordEncoder,
                                 PasswordPolicyValidator passwordPolicyValidator,
                                 RefreshTokenService refreshTokenService) {
        this.sellerRepository = sellerRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public void forgotPassword(String email) {
        // RF-AU16: identical outward behavior whether the email exists or
        // not — the caller gets no observable signal either way, only the
        // server log differs (and only because there is no email
        // provider to actually deliver the link to, see PROGRESS.md).
        sellerRepository.findByEmail(email).ifPresent(seller -> {
            String rawToken = SecureTokenGenerator.generate();
            PasswordResetToken token = new PasswordResetToken(UUID.randomUUID(), seller.getId(),
                    Sha256Hasher.hashHex(rawToken), Instant.now().plus(RESET_TOKEN_TTL_HOURS, ChronoUnit.HOURS), null);
            passwordResetTokenRepository.save(token);
            log.info("Password reset token issued for seller {}: {}", seller.getId(), rawToken);
        });
    }

    @Override
    public void resetPassword(String rawToken, String newPassword) {
        passwordPolicyValidator.validate(newPassword);

        String tokenHash = Sha256Hasher.hashHex(rawToken);
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidPasswordResetTokenException("Token de redefinição de senha inválido ou expirado."));

        Instant now = Instant.now();
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(now)) {
            throw new InvalidPasswordResetTokenException("Token de redefinição de senha inválido ou expirado.");
        }

        Seller seller = sellerRepository.findById(token.getSellerId())
                .orElseThrow(() -> new InvalidPasswordResetTokenException("Token de redefinição de senha inválido ou expirado."));

        seller.setPasswordHash(passwordEncoder.encode(newPassword));
        seller.setFailedLoginAttempts(0);
        seller.setLockedUntil(null);
        seller.setUpdatedAt(now);
        sellerRepository.save(seller);

        token.setUsedAt(now);
        passwordResetTokenRepository.save(token);

        // This checklist item explicitly calls for revoking old refresh
        // tokens on reset — every existing session must die, since the
        // whole point of a reset is "I might have lost control of my
        // account".
        refreshTokenService.revokeAllActiveTokensFor(seller.getId());
    }
}
