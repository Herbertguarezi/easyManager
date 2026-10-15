package com.guarezi.easymanager.auth.application;

import com.guarezi.easymanager.auth.application.ports.in.ConfirmEmailUseCase;
import com.guarezi.easymanager.auth.application.ports.out.EmailConfirmationTokenRepository;
import com.guarezi.easymanager.auth.application.ports.out.SellerRepository;
import com.guarezi.easymanager.auth.domain.EmailConfirmationToken;
import com.guarezi.easymanager.auth.domain.InvalidEmailConfirmationTokenException;
import com.guarezi.easymanager.auth.domain.Seller;
import com.guarezi.easymanager.auth.domain.SellerStatus;
import com.guarezi.easymanager.shared.security.Sha256Hasher;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class EmailConfirmationService implements ConfirmEmailUseCase {

    private final EmailConfirmationTokenRepository tokenRepository;
    private final SellerRepository sellerRepository;

    public EmailConfirmationService(EmailConfirmationTokenRepository tokenRepository,
                                     SellerRepository sellerRepository) {
        this.tokenRepository = tokenRepository;
        this.sellerRepository = sellerRepository;
    }

    @Override
    public void confirmEmail(String rawToken) {
        String tokenHash = Sha256Hasher.hashHex(rawToken);
        EmailConfirmationToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidEmailConfirmationTokenException("Token de confirmação inválido ou expirado."));

        Instant now = Instant.now();
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(now)) {
            throw new InvalidEmailConfirmationTokenException("Token de confirmação inválido ou expirado.");
        }

        Seller seller = sellerRepository.findById(token.getSellerId())
                .orElseThrow(() -> new InvalidEmailConfirmationTokenException("Token de confirmação inválido ou expirado."));

        seller.setEmailConfirmedAt(now);
        seller.setStatus(SellerStatus.ACTIVE);
        seller.setUpdatedAt(now);
        sellerRepository.save(seller);

        token.setUsedAt(now);
        tokenRepository.save(token);
    }
}
