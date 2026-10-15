package com.guarezi.easymanager.billing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface TokenUsageJpaRepository extends JpaRepository<TokenUsageEntity, UUID> {

    @Query("SELECT COALESCE(SUM(t.totalTokens), 0) FROM TokenUsageEntity t WHERE t.subscriptionId = :subscriptionId")
    long sumAllTokensUsed(@Param("subscriptionId") UUID subscriptionId);

    @Query("SELECT COALESCE(SUM(t.totalTokens), 0) FROM TokenUsageEntity t "
            + "WHERE t.subscriptionId = :subscriptionId AND t.referenceCycle = :referenceCycle")
    long sumTokensUsedInCycle(@Param("subscriptionId") UUID subscriptionId, @Param("referenceCycle") LocalDate referenceCycle);
}
