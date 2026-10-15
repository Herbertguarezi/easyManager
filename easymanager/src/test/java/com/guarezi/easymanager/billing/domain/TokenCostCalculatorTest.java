package com.guarezi.easymanager.billing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Fase 1.5, Block D (RN-TOKEN-001/006/007).
class TokenCostCalculatorTest {

    @Test
    void weightedAverageEqualsThePriceWhenInputAndOutputPricesAreTheSame() {
        ApiPricing pricing = apiPricing("4.5000", "4.5000");

        BigDecimal result = TokenCostCalculator.weightedAverageCostPerTokenUsd(pricing);

        assertThat(result.setScale(10, RoundingMode.HALF_UP))
                .isEqualByComparingTo(new BigDecimal("4.5000").divide(BigDecimal.valueOf(1_000_000), 10, RoundingMode.HALF_UP));
    }

    @Test
    void weightedAverageUsesTheTwelveThousandFiveHundredToSixThousandRatioNotANaiveMean() {
        // RN-TOKEN-006: input:output token ratio per execution is ~2:1
        // (12,500/6,000) — the weighted result must sit closer to the
        // input price than a plain 50/50 average would, since input
        // tokens outnumber output tokens almost 2 to 1.
        ApiPricing pricing = apiPricing("3.0000", "15.0000");

        BigDecimal weighted = TokenCostCalculator.weightedAverageCostPerTokenUsd(pricing).multiply(BigDecimal.valueOf(1_000_000));
        BigDecimal naiveMean = new BigDecimal("9.0000");

        assertThat(weighted).isLessThan(naiveMean);
        assertThat(weighted).isGreaterThan(new BigDecimal("3.0000"));
    }

    @Test
    void monthlyTokenQuotaFloorsAndNeverRoundsToNearest() {
        // RN-TOKEN-007: floor, never round-to-nearest — chosen so that
        // the exact (non-floored) result is not an integer.
        Plan plan = plan("45.00", "0.4600");
        BigDecimal costPerTokenUsd = new BigDecimal("0.00001");
        BigDecimal usdBrlRate = new BigDecimal("5");
        // maxCostBrl = 45 * 0.54 = 24.30; costPerTokenBrl = 0.00005;
        // 24.30 / 0.00005 = 486000 exactly — verifies the pipeline with a
        // clean number, floor behavior is covered by the next case.
        assertThat(TokenCostCalculator.monthlyTokenQuota(plan, costPerTokenUsd, usdBrlRate)).isEqualTo(486_000L);
    }

    @Test
    void monthlyTokenQuotaFloorsAFractionalResultDown() {
        Plan plan = plan("10.00", "0.0000");
        BigDecimal costPerTokenUsd = new BigDecimal("0.0000003"); // costPerTokenBrl = 0.0000015
        BigDecimal usdBrlRate = new BigDecimal("5");
        // 10 / 0.0000015 = 6,666,666.666... -> floors to 6,666,666.
        assertThat(TokenCostCalculator.monthlyTokenQuota(plan, costPerTokenUsd, usdBrlRate)).isEqualTo(6_666_666L);
    }

    private ApiPricing apiPricing(String inputPrice, String outputPrice) {
        ApiPricing pricing = new ApiPricing();
        pricing.setId(UUID.randomUUID());
        pricing.setInputPriceUsdPerMillion(new BigDecimal(inputPrice));
        pricing.setOutputPriceUsdPerMillion(new BigDecimal(outputPrice));
        pricing.setEffectiveFrom(Instant.now());
        pricing.setCreatedAt(Instant.now());
        return pricing;
    }

    private Plan plan(String monthlyPrice, String targetMargin) {
        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setModule(Module.ADS_ANALYZER);
        plan.setName("Completo");
        plan.setStoresLimit(5);
        plan.setMonthlyPrice(new BigDecimal(monthlyPrice));
        plan.setTargetMargin(new BigDecimal(targetMargin));
        return plan;
    }
}
