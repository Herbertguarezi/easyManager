package com.guarezi.easymanager.auth.application;

import com.guarezi.easymanager.auth.application.ports.in.RegisterSellerUseCase;
import com.guarezi.easymanager.auth.application.ports.out.EmailConfirmationTokenRepository;
import com.guarezi.easymanager.auth.application.ports.out.SellerRepository;
import com.guarezi.easymanager.auth.domain.EmailConfirmationToken;
import com.guarezi.easymanager.auth.domain.Role;
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
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService implements RegisterSellerUseCase {

    // No TTL is specified in the docs for this token; 24h is a common,
    // reasonable default for an email confirmation link (see PROGRESS.md
    // Fase 1 decisions log).
    private static final int CONFIRMATION_TOKEN_TTL_HOURS = 24;

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final SellerRepository sellerRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final EmailConfirmationTokenRepository emailConfirmationTokenRepository;

    public AuthService(SellerRepository sellerRepository, PasswordEncoder passwordEncoder,
                        PasswordPolicyValidator passwordPolicyValidator,
                        EmailConfirmationTokenRepository emailConfirmationTokenRepository) {
        this.sellerRepository = sellerRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.emailConfirmationTokenRepository = emailConfirmationTokenRepository;
    }

    @Override
    public Seller register(String email, String rawPassword) {
        passwordPolicyValidator.validate(rawPassword);

        // RF-AU16: never reveal, through the response, whether this email
        // was already registered — and if it was, never reveal the real
        // account's id or its real status either (found in review: an
        // already-confirmed account used to leak "status": "ACTIVE" and
        // its real id here, a bigger enumeration hole than intended). The
        // response is shaped to look exactly like a fresh registration,
        // with a fresh random id, no matter what the real account's state
        // is. The password is still hashed (even though the result is
        // discarded) so this branch's cost stays close to the real
        // registration path below — a bare short-circuit here would
        // otherwise leak existence via response timing.
        Optional<Seller> existing = sellerRepository.findByEmail(email);
        if (existing.isPresent()) {
            passwordEncoder.encode(rawPassword);
            return fakeRegistrationResult(email);
        }

        Instant now = Instant.now();

        Seller seller = new Seller();
        seller.setId(UUID.randomUUID());
        seller.setEmail(email);
        seller.setPasswordHash(passwordEncoder.encode(rawPassword));
        seller.setRole(Role.SELLER);
        seller.setStatus(SellerStatus.PENDING);
        seller.setCreatedAt(now);
        seller.setUpdatedAt(now);

        Seller saved = sellerRepository.save(seller);
        issueEmailConfirmationToken(saved);
        return saved;
    }

    private Seller fakeRegistrationResult(String email) {
        Seller fake = new Seller();
        fake.setId(UUID.randomUUID());
        fake.setEmail(email);
        fake.setStatus(SellerStatus.PENDING);
        return fake;
    }

    private void issueEmailConfirmationToken(Seller seller) {
        String rawToken = SecureTokenGenerator.generate();
        EmailConfirmationToken token = new EmailConfirmationToken(
                UUID.randomUUID(), seller.getId(), Sha256Hasher.hashHex(rawToken),
                Instant.now().plus(CONFIRMATION_TOKEN_TTL_HOURS, ChronoUnit.HOURS), null);
        emailConfirmationTokenRepository.save(token);

        // No email provider is wired up yet (see PROGRESS.md Fase 1
        // decisions log) — logging the raw token stands in for sending it,
        // so the confirmation flow is fully exercisable end to end.
        log.info("Email confirmation token issued for seller {}: {}", seller.getId(), rawToken);
    }
}
