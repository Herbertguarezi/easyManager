-- Fase 1.5, achado na revisão do Block A: the original
-- chk_subscriptions_plan_required (V6) did not exempt DELETED, but
-- TRIAL_* -> DELETED (30-day ADS_ANALYZER trial expiry, RN-TRIAL-006) is
-- a legitimate transition that never had a plan. V6 is already applied
-- and is never edited; this corrects the constraint going forward. See
-- docs/easy-manager-arquitetura-banco-de-dados.md sec. 3 and PROGRESS.md
-- Fase 1.5 Block A decisions log.
ALTER TABLE subscriptions
    DROP CONSTRAINT chk_subscriptions_plan_required,
    ADD CONSTRAINT chk_subscriptions_plan_required CHECK (
        status IN ('TRIAL_ACTIVE', 'TRIAL_RESTRICTED', 'TRIAL_EXHAUSTED', 'DELETED') OR plan_id IS NOT NULL);
