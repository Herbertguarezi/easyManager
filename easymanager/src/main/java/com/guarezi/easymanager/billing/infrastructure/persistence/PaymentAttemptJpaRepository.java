package com.guarezi.easymanager.billing.infrastructure.persistence;

import com.guarezi.easymanager.billing.domain.PaymentAttemptResult;
import com.guarezi.easymanager.billing.domain.PaymentAttemptType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaymentAttemptJpaRepository extends JpaRepository<PaymentAttemptEntity, UUID> {
    long countBySubscriptionIdAndTypeAndResult(UUID subscriptionId, PaymentAttemptType type, PaymentAttemptResult result);
}
