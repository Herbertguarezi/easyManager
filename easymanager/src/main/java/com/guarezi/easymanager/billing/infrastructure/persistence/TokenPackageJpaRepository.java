package com.guarezi.easymanager.billing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface TokenPackageJpaRepository extends JpaRepository<TokenPackageEntity, UUID> {

    @Query("SELECT COALESCE(SUM(t.tokensPurchased), 0) FROM TokenPackageEntity t "
            + "WHERE t.subscriptionId = :subscriptionId AND t.referenceCycle = :referenceCycle")
    long sumTokensPurchasedInCycle(@Param("subscriptionId") UUID subscriptionId, @Param("referenceCycle") LocalDate referenceCycle);
}
