package com.guarezi.easymanager.billing.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// Fase 1.5, Block A: verifies the migrations themselves (tables, columns,
// seed data) directly against the schema — no JPA entities exist yet for
// this bounded context (those come with Block B/C, when the state machine
// and EntitlementService need to read/write these tables).
@SpringBootTest
class BillingSchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void plansTableHasExpectedColumnsAndSixSeedRows() throws Exception {
        assertThat(columnNames("plans")).containsExactlyInAnyOrder("id", "module", "name", "stores_limit",
                "monthly_price", "target_margin", "current_month_token_quota", "created_at", "updated_at");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT module, name, stores_limit, monthly_price, target_margin FROM plans");
        assertThat(rows).hasSize(6);

        assertThat(rows).extracting(r -> r.get("module")).containsOnly("PRODUCTS", "ADS_ANALYZER");

        Map<String, Object> productsBasico = jdbcTemplate.queryForMap(
                "SELECT name, stores_limit, monthly_price, target_margin FROM plans WHERE module = 'PRODUCTS' AND stores_limit = 1");
        assertThat(productsBasico.get("name")).isEqualTo("Básico");
        assertThat(((BigDecimal) productsBasico.get("monthly_price")).compareTo(new BigDecimal("15.00"))).isZero();
        assertThat(productsBasico.get("target_margin")).isNull();

        Map<String, Object> adsCompleto = jdbcTemplate.queryForMap(
                "SELECT name, stores_limit, monthly_price, target_margin FROM plans WHERE module = 'ADS_ANALYZER' AND stores_limit = 5");
        assertThat(adsCompleto.get("name")).isEqualTo("Completo");
        assertThat(((BigDecimal) adsCompleto.get("monthly_price")).compareTo(new BigDecimal("149.00"))).isZero();
        assertThat(((BigDecimal) adsCompleto.get("target_margin")).compareTo(new BigDecimal("0.5400"))).isZero();
    }

    @Test
    void apiPricingHistoryTableHasExpectedColumns() throws Exception {
        assertThat(columnNames("api_pricing_history")).containsExactlyInAnyOrder("id",
                "input_price_usd_per_million", "output_price_usd_per_million", "effective_from", "updated_by",
                "created_at");
    }

    @Test
    void apiPricingHistoryHasTheSeededSonnetPricing() {
        // Fase 1.5, Block D: first manual entry (RN-TOKEN-008), confirmed
        // with the user — Claude Sonnet, $3.00 input / $15.00 output per
        // million tokens. Flyway runs V12 before any test method, so the
        // earliest row by created_at is always the migration's seed, even
        // though other tests in this class insert rows with the same
        // values later.
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT input_price_usd_per_million, output_price_usd_per_million, updated_by "
                        + "FROM api_pricing_history ORDER BY created_at ASC LIMIT 1");

        assertThat(((BigDecimal) row.get("input_price_usd_per_million")).compareTo(new BigDecimal("3.0000"))).isZero();
        assertThat(((BigDecimal) row.get("output_price_usd_per_million")).compareTo(new BigDecimal("15.0000"))).isZero();
        assertThat(row.get("updated_by")).isNull();
    }

    @Test
    void exchangeRateSnapshotsTableHasExpectedColumns() throws Exception {
        assertThat(columnNames("exchange_rate_snapshots")).containsExactlyInAnyOrder("id", "usd_brl_rate",
                "reference_date", "created_at");
    }

    @Test
    void subscriptionsTableHasExpectedColumns() throws Exception {
        assertThat(columnNames("subscriptions")).containsExactlyInAnyOrder("id", "seller_id", "module", "plan_id",
                "status", "billing_cycle", "anniversary_date", "current_cycle_start_date", "current_cycle_end_date",
                "current_token_period_start_date", "current_token_period_end_date", "combo_group_id",
                "cancellation_requested_at", "blocked_at", "scheduled_deletion_date", "trial_expires_at",
                "created_at", "updated_at");
    }

    @Test
    void subscriptionsRejectsAPaidStatusWithoutAPlan() {
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        "INSERT INTO subscriptions (id, seller_id, module, plan_id, status, created_at, updated_at) "
                                + "VALUES (UUID(), ?, 'PRODUCTS', NULL, 'ACTIVE', UTC_TIMESTAMP(), UTC_TIMESTAMP())",
                        seedSeller()));
    }

    @Test
    void subscriptionsAllowsAnExcludedStatusWithoutAPlan() {
        // Achado na revisão do Block A: EXCLUIDO/DELETED is reachable
        // without ever having had a plan (TRIAL_* -> DELETED, 30-day trial
        // expiry, RN-TRIAL-006) — arquitetura-banco-de-dados.md sec. 3
        // corrected chk_subscriptions_plan_required to exempt it too.
        jdbcTemplate.update(
                "INSERT INTO subscriptions (id, seller_id, module, plan_id, status, created_at, updated_at) "
                        + "VALUES (UUID(), ?, 'ADS_ANALYZER', NULL, 'DELETED', UTC_TIMESTAMP(), UTC_TIMESTAMP())",
                seedSeller());
    }

    @Test
    void subscriptionsAllowsAWithdrawnStatusWithoutAPlan() {
        // Fase 1.5, Block B: new 9th status (RN-PAY-002/004, right of
        // withdrawal) — never had a plan by the time it's reached from
        // ACTIVE, since plan_id is cleared on withdrawal (see
        // PROGRESS.md Fase 1.5 Block B decisions log).
        jdbcTemplate.update(
                "INSERT INTO subscriptions (id, seller_id, module, plan_id, status, created_at, updated_at) "
                        + "VALUES (UUID(), ?, 'ADS_ANALYZER', NULL, 'WITHDRAWN', UTC_TIMESTAMP(), UTC_TIMESTAMP())",
                seedSeller());
    }

    @Test
    void subscriptionsAllowsATrialStatusWithoutAPlan() {
        jdbcTemplate.update(
                "INSERT INTO subscriptions (id, seller_id, module, plan_id, status, created_at, updated_at) "
                        + "VALUES (UUID(), ?, 'PRODUCTS', NULL, 'TRIAL_ACTIVE', UTC_TIMESTAMP(), UTC_TIMESTAMP())",
                seedSeller());
    }

    @Test
    void tokenUsageTableHasExpectedColumnsAndComputesTotalTokens() throws Exception {
        assertThat(columnNames("token_usage")).containsExactlyInAnyOrder("id", "analysis_run_id", "subscription_id",
                "store_id", "source", "input_tokens", "output_tokens", "total_tokens", "api_pricing_id",
                "exchange_rate_id", "cost_brl_snapshot", "reference_cycle", "created_at");

        String subscriptionId = seedAdsAnalyzerSubscription();
        String apiPricingId = seedApiPricingHistory();
        String exchangeRateId = seedExchangeRateSnapshot();

        jdbcTemplate.update(
                "INSERT INTO token_usage (id, analysis_run_id, subscription_id, store_id, source, input_tokens, "
                        + "output_tokens, api_pricing_id, exchange_rate_id, cost_brl_snapshot, reference_cycle, created_at) "
                        + "VALUES (UUID(), UUID(), ?, UUID(), 'TRIAL', 12500, 6000, ?, ?, 1.2345, CURRENT_DATE(), UTC_TIMESTAMP())",
                subscriptionId, apiPricingId, exchangeRateId);

        Integer totalTokens = jdbcTemplate.queryForObject(
                "SELECT total_tokens FROM token_usage WHERE subscription_id = ?", Integer.class, subscriptionId);
        assertThat(totalTokens).isEqualTo(18500);
    }

    @Test
    void tokenPackagePlansTableHasExpectedColumnsAndThreeSeedRows() throws Exception {
        assertThat(columnNames("token_package_plans")).containsExactlyInAnyOrder("id", "size", "tokens", "price_brl",
                "created_at", "updated_at");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT size, tokens, price_brl FROM token_package_plans");
        assertThat(rows).hasSize(3);

        Map<String, Object> small = jdbcTemplate.queryForMap(
                "SELECT tokens, price_brl FROM token_package_plans WHERE size = 'S'");
        assertThat(small.get("tokens")).isEqualTo(50000);
        assertThat(((BigDecimal) small.get("price_brl")).compareTo(new BigDecimal("19.50"))).isZero();
    }

    @Test
    void tokenPackagesTableHasExpectedColumns() throws Exception {
        assertThat(columnNames("token_packages")).containsExactlyInAnyOrder("id", "subscription_id",
                "package_plan_id", "tokens_purchased", "price_paid_brl", "reference_cycle", "expires_at", "created_at");

        String subscriptionId = seedAdsAnalyzerSubscription();
        String packagePlanId = jdbcTemplate.queryForObject(
                "SELECT id FROM token_package_plans WHERE size = 'M'", String.class);

        jdbcTemplate.update(
                "INSERT INTO token_packages (id, subscription_id, package_plan_id, tokens_purchased, price_paid_brl, "
                        + "reference_cycle, expires_at, created_at) "
                        + "VALUES (UUID(), ?, ?, 100000, 39.00, CURRENT_DATE(), CURRENT_DATE(), UTC_TIMESTAMP())",
                subscriptionId, packagePlanId);
    }

    @Test
    void paymentAttemptsTableHasExpectedColumns() throws Exception {
        assertThat(columnNames("payment_attempts")).containsExactlyInAnyOrder("id", "subscription_id", "type",
                "attempt_number", "attempted_at", "result", "decline_reason", "gateway_transaction_id", "created_at");

        String subscriptionId = seedAdsAnalyzerSubscription();
        jdbcTemplate.update(
                "INSERT INTO payment_attempts (id, subscription_id, type, attempt_number, attempted_at, result, created_at) "
                        + "VALUES (UUID(), ?, 'INITIAL_CHARGE', 1, UTC_TIMESTAMP(), 'APPROVED', UTC_TIMESTAMP())",
                subscriptionId);
    }

    @Test
    void refundsTableHasExpectedColumns() throws Exception {
        assertThat(columnNames("refunds")).containsExactlyInAnyOrder("id", "subscription_id", "type", "amount_brl",
                "requested_at", "processed_at", "gateway_transaction_id", "created_at", "updated_at");

        String subscriptionId = seedAdsAnalyzerSubscription();
        jdbcTemplate.update(
                "INSERT INTO refunds (id, subscription_id, type, amount_brl, requested_at, created_at, updated_at) "
                        + "VALUES (UUID(), ?, 'FULL_WITHDRAWAL', 39.00, UTC_TIMESTAMP(), UTC_TIMESTAMP(), UTC_TIMESTAMP())",
                subscriptionId);
    }

    @Test
    void trialContestationsTableHasExpectedColumns() throws Exception {
        assertThat(columnNames("trial_contestations")).containsExactlyInAnyOrder("id", "subscription_id",
                "justification", "status", "reviewed_by", "reviewed_at", "created_at", "updated_at");

        String subscriptionId = seedAdsAnalyzerSubscription();
        jdbcTemplate.update(
                "INSERT INTO trial_contestations (id, subscription_id, justification, created_at, updated_at) "
                        + "VALUES (UUID(), ?, 'sou cônjuge do titular de outra conta', UTC_TIMESTAMP(), UTC_TIMESTAMP())",
                subscriptionId);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM trial_contestations WHERE subscription_id = ?", String.class, subscriptionId);
        assertThat(status).isEqualTo("PENDING");
    }

    @Test
    void riskSignalsTableHasExpectedColumns() throws Exception {
        assertThat(columnNames("risk_signals")).containsExactlyInAnyOrder("id", "subscription_id", "signal_type",
                "detail", "detected_at");

        String subscriptionId = seedAdsAnalyzerSubscription();
        jdbcTemplate.update(
                "INSERT INTO risk_signals (id, subscription_id, signal_type, detected_at) "
                        + "VALUES (UUID(), ?, 'DEVICE_FINGERPRINT', UTC_TIMESTAMP())",
                subscriptionId);
    }

    private String seedAdsAnalyzerSubscription() {
        String sellerId = seedSeller();
        String planId = jdbcTemplate.queryForObject(
                "SELECT id FROM plans WHERE module = 'ADS_ANALYZER' AND stores_limit = 1", String.class);
        String subscriptionId = java.util.UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO subscriptions (id, seller_id, module, plan_id, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'ADS_ANALYZER', ?, 'ACTIVE', UTC_TIMESTAMP(), UTC_TIMESTAMP())",
                subscriptionId, sellerId, planId);
        return subscriptionId;
    }

    private String seedApiPricingHistory() {
        String id = java.util.UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO api_pricing_history (id, input_price_usd_per_million, output_price_usd_per_million, "
                        + "effective_from, created_at) VALUES (?, 3.0000, 15.0000, UTC_TIMESTAMP(), UTC_TIMESTAMP())",
                id);
        return id;
    }

    private String seedExchangeRateSnapshot() {
        // Random date within a wide historical range, not "today" — avoids
        // colliding with UNIQUE(reference_date) across repeated test runs
        // against the same persistent test database.
        String id = java.util.UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO exchange_rate_snapshots (id, usd_brl_rate, reference_date, created_at) "
                        + "VALUES (?, 5.2000, DATE('2000-01-01') + INTERVAL FLOOR(RAND() * 36500) DAY, UTC_TIMESTAMP())",
                id);
        return id;
    }

    private String seedSeller() {
        String sellerId = java.util.UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO sellers (id, email, password_hash, role, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'hash', 'SELLER', 'ACTIVE', UTC_TIMESTAMP(), UTC_TIMESTAMP())",
                sellerId, sellerId + "@example.com");
        return sellerId;
    }

    private Set<String> columnNames(String table) throws Exception {
        Set<String> columnNames = new HashSet<>();
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet columns = metaData.getColumns(null, null, table, null)) {
                while (columns.next()) {
                    columnNames.add(columns.getString("COLUMN_NAME").toLowerCase());
                }
            }
        }
        return columnNames;
    }
}
