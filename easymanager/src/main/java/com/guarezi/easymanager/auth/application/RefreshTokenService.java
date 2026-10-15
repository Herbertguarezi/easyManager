package com.guarezi.easymanager.auth.application;

import com.guarezi.easymanager.auth.application.ports.in.AuthTokens;
import com.guarezi.easymanager.auth.application.ports.in.RefreshAccessTokenUseCase;
import com.guarezi.easymanager.auth.application.ports.out.AccessTokenIssuer;
import com.guarezi.easymanager.auth.application.ports.out.RefreshTokenRepository;
import com.guarezi.easymanager.auth.application.ports.out.SellerRepository;
import com.guarezi.easymanager.auth.domain.RefreshToken;
import com.guarezi.easymanager.auth.domain.Seller;
import com.guarezi.easymanager.shared.exceptions.AuthenticationException;
import com.guarezi.easymanager.shared.security.Sha256Hasher;
import com.guarezi.easymanager.shared.security.SecureTokenGenerator;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

// §4.1: refresh token rotation — each use revokes the presented token and
// issues a new one; presenting an already-revoked token is treated as a
// sign of compromise (§4.1, "detecção de reuso"). No TTL is specified in
// the docs for the refresh token itself — 7 days is this implementation's
// choice (see PROGRESS.md Fase 1 decisions log).
@Service
public class RefreshTokenService implements RefreshAccessTokenUseCase {

    public static final int REFRESH_TOKEN_TTL_DAYS = 7;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SellerRepository sellerRepository;
    private final AccessTokenIssuer accessTokenIssuer;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, SellerRepository sellerRepository,
                                AccessTokenIssuer accessTokenIssuer) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.sellerRepository = sellerRepository;
        this.accessTokenIssuer = accessTokenIssuer;
    }

    public AuthTokens issueNewPair(Seller seller) {
        MintedToken minted = createAndSaveToken(seller.getId());
        String accessToken = accessTokenIssuer.issue(seller.getId(), seller.getRole());
        return new AuthTokens(accessToken, minted.raw());
    }

    @Override
    public AuthTokens refresh(String rawRefreshToken) {
        String tokenHash = Sha256Hasher.hashHex(rawRefreshToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AuthenticationException("unknown refresh token"));

        if (token.getRevokedAt() != null) {
            revokeAllActiveTokensFor(token.getSellerId());
            throw new AuthenticationException("refresh token reuse detected for seller " + token.getSellerId());
        }

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new AuthenticationException("expired refresh token");
        }

        Seller seller = sellerRepository.findById(token.getSellerId())
                .orElseThrow(() -> new AuthenticationException("seller not found for refresh token"));

        MintedToken minted = createAndSaveToken(seller.getId());

        token.setRevokedAt(Instant.now());
        token.setReplacedBy(minted.persisted().getId());
        refreshTokenRepository.save(token);

        String accessToken = accessTokenIssuer.issue(seller.getId(), seller.getRole());
        return new AuthTokens(accessToken, minted.raw());
    }

    // Also used by PasswordResetService: resetting a password must
    // invalidate every existing session, same precaution as reuse
    // detection above.
    public void revokeAllActiveTokensFor(UUID sellerId) {
        Instant now = Instant.now();
        for (RefreshToken activeToken : refreshTokenRepository.findActiveBySellerId(sellerId)) {
            activeToken.setRevokedAt(now);
            refreshTokenRepository.save(activeToken);
        }
    }

    private MintedToken createAndSaveToken(UUID sellerId) {
        String raw = SecureTokenGenerator.generate();
        RefreshToken token = new RefreshToken(UUID.randomUUID(), sellerId, Sha256Hasher.hashHex(raw),
                Instant.now().plus(REFRESH_TOKEN_TTL_DAYS, ChronoUnit.DAYS), Instant.now(), null, null);
        RefreshToken saved = refreshTokenRepository.save(token);
        return new MintedToken(saved, raw);
    }

    private record MintedToken(RefreshToken persisted, String raw) {
    }
}
