package com.guarezi.easymanager.billing.infrastructure.persistence;

import com.guarezi.easymanager.billing.application.ports.out.PaymentAttemptRepository;
import com.guarezi.easymanager.billing.domain.PaymentAttemptResult;
import com.guarezi.easymanager.billing.domain.PaymentAttemptType;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PaymentAttemptRepositoryPersistenceAdapter implements PaymentAttemptRepository {

    private final PaymentAttemptJpaRepository repository;

    public PaymentAttemptRepositoryPersistenceAdapter(PaymentAttemptJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public long countDeclinedRenewalAttempts(UUID subscriptionId) {
        return repository.countBySubscriptionIdAndTypeAndResult(
                subscriptionId, PaymentAttemptType.RENEWAL, PaymentAttemptResult.DECLINED);
    }
}
