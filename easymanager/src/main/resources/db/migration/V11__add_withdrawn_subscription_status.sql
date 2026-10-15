-- Fase 1.5, Block B: 9th status, not in the original 8-state machine of
-- regras-negocio.md sec. 13 (confirmed with the user during Block B
-- review — see PROGRESS.md Fase 1.5 Block B decisions log). ACTIVE ->
-- WITHDRAWN models the right of withdrawal (RN-PAY-002/004): access is
-- cut immediately, plan_id is cleared, and — unlike DELETED — this is
-- never reached with a retention period, so it needs its own value
-- rather than reusing DELETED's terminal/post-retention semantics.
ALTER TABLE subscriptions
    DROP CONSTRAINT chk_subscriptions_status,
    ADD CONSTRAINT chk_subscriptions_status CHECK (status IN (
        'TRIAL_ACTIVE', 'TRIAL_RESTRICTED', 'TRIAL_EXHAUSTED', 'ACTIVE',
        'PAST_DUE', 'CANCELLATION_REQUESTED', 'RETENTION', 'DELETED', 'WITHDRAWN')),
    DROP CONSTRAINT chk_subscriptions_plan_required,
    ADD CONSTRAINT chk_subscriptions_plan_required CHECK (
        status IN ('TRIAL_ACTIVE', 'TRIAL_RESTRICTED', 'TRIAL_EXHAUSTED', 'DELETED', 'WITHDRAWN') OR plan_id IS NOT NULL);
