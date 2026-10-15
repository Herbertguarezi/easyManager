CREATE TABLE subscriptions (
    id CHAR(36) NOT NULL,
    seller_id CHAR(36) NOT NULL,
    module VARCHAR(30) NOT NULL,
    plan_id CHAR(36) NULL,
    status VARCHAR(30) NOT NULL,
    billing_cycle VARCHAR(20) NULL,
    anniversary_date DATE NULL,
    current_cycle_start_date DATE NULL,
    current_cycle_end_date DATE NULL,
    current_token_period_start_date DATE NULL,
    current_token_period_end_date DATE NULL,
    combo_group_id CHAR(36) NULL,
    cancellation_requested_at DATETIME NULL,
    blocked_at DATETIME NULL,
    scheduled_deletion_date DATE NULL,
    trial_expires_at DATE NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_subscriptions_seller_module UNIQUE (seller_id, module),
    CONSTRAINT fk_subscriptions_seller FOREIGN KEY (seller_id) REFERENCES sellers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES plans (id) ON DELETE RESTRICT,
    CONSTRAINT chk_subscriptions_module CHECK (module IN ('PRODUCTS', 'ADS_ANALYZER')),
    -- regras-negocio.md sec. 13.1: SEM_ASSINATURA is not a stored value,
    -- it is the absence of a row for that seller x module.
    CONSTRAINT chk_subscriptions_status CHECK (status IN (
        'TRIAL_ACTIVE', 'TRIAL_RESTRICTED', 'TRIAL_EXHAUSTED', 'ACTIVE',
        'PAST_DUE', 'CANCELLATION_REQUESTED', 'RETENTION', 'DELETED')),
    CONSTRAINT chk_subscriptions_billing_cycle CHECK (billing_cycle IS NULL OR billing_cycle IN (
        'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL')),
    -- No paid state exists without a plan (arquitetura-banco-de-dados.md sec. 3).
    CONSTRAINT chk_subscriptions_plan_required CHECK (
        status IN ('TRIAL_ACTIVE', 'TRIAL_RESTRICTED', 'TRIAL_EXHAUSTED') OR plan_id IS NOT NULL)
);

CREATE INDEX idx_subscriptions_status ON subscriptions (status);
CREATE INDEX idx_subscriptions_combo_group_id ON subscriptions (combo_group_id);
CREATE INDEX idx_subscriptions_scheduled_deletion_date ON subscriptions (scheduled_deletion_date);
