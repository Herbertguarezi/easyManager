package com.guarezi.easymanager.billing.infrastructure.persistence;

import com.guarezi.easymanager.billing.application.ports.out.ApiPricingRepository;
import com.guarezi.easymanager.billing.domain.ApiPricing;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ApiPricingRepositoryPersistenceAdapter implements ApiPricingRepository {

    private final ApiPricingJpaRepository repository;

    public ApiPricingRepositoryPersistenceAdapter(ApiPricingJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ApiPricing> findCurrent() {
        return repository.findFirstByOrderByEffectiveFromDesc().map(this::toDomain);
    }

    private ApiPricing toDomain(ApiPricingEntity entity) {
        if (entity == null) return null;

        ApiPricing apiPricing = new ApiPricing();
        apiPricing.setId(entity.getId());
        apiPricing.setInputPriceUsdPerMillion(entity.getInputPriceUsdPerMillion());
        apiPricing.setOutputPriceUsdPerMillion(entity.getOutputPriceUsdPerMillion());
        apiPricing.setEffectiveFrom(entity.getEffectiveFrom());
        apiPricing.setUpdatedBy(entity.getUpdatedBy());
        apiPricing.setCreatedAt(entity.getCreatedAt());
        return apiPricing;
    }
}
