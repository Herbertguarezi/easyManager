package com.guarezi.easymanager.billing.infrastructure.persistence;

import com.guarezi.easymanager.billing.application.ports.out.TokenLedgerRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Component
public class TokenLedgerRepositoryPersistenceAdapter implements TokenLedgerRepository {

    private final TokenUsageJpaRepository tokenUsageJpaRepository;
    private final TokenPackageJpaRepository tokenPackageJpaRepository;

    public TokenLedgerRepositoryPersistenceAdapter(TokenUsageJpaRepository tokenUsageJpaRepository,
                                                     TokenPackageJpaRepository tokenPackageJpaRepository) {
        this.tokenUsageJpaRepository = tokenUsageJpaRepository;
        this.tokenPackageJpaRepository = tokenPackageJpaRepository;
    }

    @Override
    public long sumAllTokensUsed(UUID subscriptionId) {
        return tokenUsageJpaRepository.sumAllTokensUsed(subscriptionId);
    }

    @Override
    public long sumTokensUsedInCycle(UUID subscriptionId, LocalDate referenceCycle) {
        return tokenUsageJpaRepository.sumTokensUsedInCycle(subscriptionId, referenceCycle);
    }

    @Override
    public long sumTokensPurchasedInCycle(UUID subscriptionId, LocalDate referenceCycle) {
        return tokenPackageJpaRepository.sumTokensPurchasedInCycle(subscriptionId, referenceCycle);
    }
}
