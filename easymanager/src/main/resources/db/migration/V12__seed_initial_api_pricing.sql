-- Fase 1.5, Block D: first entry of api_pricing_history (RN-TOKEN-008,
-- versioned history) — nothing populates this table before now, and the
-- exchange-rate job (RN-TOKEN-001/002) needs at least one row to compute
-- plans.current_month_token_quota. Values confirmed with the user: Claude
-- Sonnet, the model Fase 7's Ads Analyzer will call, at $3.00 input /
-- $15.00 output per million tokens. updated_by is NULL — this is a
-- migration seed, not an admin action through the (not yet built)
-- manual price-update flow.
INSERT INTO api_pricing_history (id, input_price_usd_per_million, output_price_usd_per_million, effective_from, updated_by, created_at)
VALUES (UUID(), 3.0000, 15.0000, UTC_TIMESTAMP(), NULL, UTC_TIMESTAMP());
