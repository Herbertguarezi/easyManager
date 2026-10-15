CREATE TABLE token_usage (
    id CHAR(36) NOT NULL,
    -- analysis_runs only exists from Fase 7 onward; born without a FK here
    -- (same type/size, CHAR(36), so the future FK in Fase 7 won't fail),
    -- see PROGRESS.md Fase 1.5 Block A decisions log.
    analysis_run_id CHAR(36) NOT NULL,
    subscription_id CHAR(36) NOT NULL,
    -- stores only exists from Fase 2 onward; same reasoning/treatment as
    -- analysis_run_id above.
    store_id CHAR(36) NOT NULL,
    source VARCHAR(20) NOT NULL,
    input_tokens INT NOT NULL,
    output_tokens INT NOT NULL,
    total_tokens INT AS (input_tokens + output_tokens) STORED,
    api_pricing_id CHAR(36) NOT NULL,
    exchange_rate_id CHAR(36) NOT NULL,
    cost_brl_snapshot DECIMAL(10, 4) NOT NULL,
    reference_cycle DATE NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_token_usage_analysis_run_id UNIQUE (analysis_run_id),
    CONSTRAINT fk_token_usage_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_token_usage_api_pricing FOREIGN KEY (api_pricing_id) REFERENCES api_pricing_history (id) ON DELETE RESTRICT,
    CONSTRAINT fk_token_usage_exchange_rate FOREIGN KEY (exchange_rate_id) REFERENCES exchange_rate_snapshots (id) ON DELETE RESTRICT,
    CONSTRAINT chk_token_usage_source CHECK (source IN ('TRIAL', 'PAID_PLAN'))
);

CREATE INDEX idx_token_usage_subscription_reference_cycle ON token_usage (subscription_id, reference_cycle);
CREATE INDEX idx_token_usage_store_id ON token_usage (store_id);

CREATE TABLE token_package_plans (
    id CHAR(36) NOT NULL,
    size VARCHAR(10) NOT NULL,
    tokens INT NOT NULL,
    price_brl DECIMAL(10, 2) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_token_package_plans_size UNIQUE (size),
    CONSTRAINT chk_token_package_plans_size CHECK (size IN ('S', 'M', 'L'))
);

-- RN-TOKEN-010: fixed catalog, manually reviewed, not recalculated by the
-- daily exchange rate job.
INSERT INTO token_package_plans (id, size, tokens, price_brl, created_at, updated_at) VALUES
    (UUID(), 'S', 50000, 19.50, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (UUID(), 'M', 100000, 39.00, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (UUID(), 'L', 150000, 58.50, UTC_TIMESTAMP(), UTC_TIMESTAMP());

CREATE TABLE token_packages (
    id CHAR(36) NOT NULL,
    subscription_id CHAR(36) NOT NULL,
    package_plan_id CHAR(36) NOT NULL,
    tokens_purchased INT NOT NULL,
    price_paid_brl DECIMAL(10, 2) NOT NULL,
    reference_cycle DATE NOT NULL,
    expires_at DATE NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_token_packages_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_token_packages_package_plan FOREIGN KEY (package_plan_id) REFERENCES token_package_plans (id) ON DELETE RESTRICT
);

CREATE INDEX idx_token_packages_subscription_reference_cycle ON token_packages (subscription_id, reference_cycle);
