package com.guarezi.easymanager.billing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

// RN-TOKEN-001/006/007, used by the daily exchange-rate job (Fase 1.5
// Block D). Pure math, no I/O — kept separate from the job itself so the
// formula can be verified precisely without mocking a repository.
public final class TokenCostCalculator {

    // RN-TOKEN-006: ~2:1 input:output token ratio per execution
    // (12,500/6,000, midpoint of the Shopee Ads Analyzer engineering
    // doc's range) — a fixed estimate. The RN also describes automatic
    // recalibration from real execution data after a minimum volume
    // (reference: 100 runs), but zero real executions exist anywhere in
    // this codebase yet (Fase 7 is what will call the Anthropic API for
    // the first time) — recalibration has nothing to recalibrate from,
    // so it is not implemented here; see PROGRESS.md Fase 1.5 Block D
    // decisions log.
    private static final BigDecimal INPUT_TOKENS_PER_EXECUTION = BigDecimal.valueOf(12_500);
    private static final BigDecimal OUTPUT_TOKENS_PER_EXECUTION = BigDecimal.valueOf(6_000);
    private static final BigDecimal TOKENS_PER_EXECUTION =
            INPUT_TOKENS_PER_EXECUTION.add(OUTPUT_TOKENS_PER_EXECUTION);
    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);
    private static final int INTERMEDIATE_SCALE = 20;

    private TokenCostCalculator() {
    }

    // USD per single token (not per million) — the weighted average
    // between Anthropic's input/output price, weighted by how many of
    // each an execution actually uses.
    public static BigDecimal weightedAverageCostPerTokenUsd(ApiPricing pricing) {
        BigDecimal weightedPerMillion = pricing.getInputPriceUsdPerMillion().multiply(INPUT_TOKENS_PER_EXECUTION)
                .add(pricing.getOutputPriceUsdPerMillion().multiply(OUTPUT_TOKENS_PER_EXECUTION))
                .divide(TOKENS_PER_EXECUTION, INTERMEDIATE_SCALE, RoundingMode.HALF_UP);

        return weightedPerMillion.divide(ONE_MILLION, INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
    }

    // RN-TOKEN-001: custo_maximo_R$ = preco_mensal x (1 - margem_alvo);
    // cota = custo_maximo_R$ / custo_por_token_atual (in BRL).
    // RN-TOKEN-007: always floors, conservative so the real cost never
    // exceeds the promised margin ceiling.
    public static long monthlyTokenQuota(Plan plan, BigDecimal costPerTokenUsd, BigDecimal usdBrlRate) {
        BigDecimal costPerTokenBrl = costPerTokenUsd.multiply(usdBrlRate);
        BigDecimal maxCostBrl = plan.getMonthlyPrice().multiply(BigDecimal.ONE.subtract(plan.getTargetMargin()));
        return maxCostBrl.divide(costPerTokenBrl, 0, RoundingMode.FLOOR).longValueExact();
    }
}
