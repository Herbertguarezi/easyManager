CREATE TABLE plans (
    id CHAR(36) NOT NULL,
    module VARCHAR(30) NOT NULL,
    name VARCHAR(50) NOT NULL,
    stores_limit INT NOT NULL,
    monthly_price DECIMAL(10, 2) NOT NULL,
    target_margin DECIMAL(5, 4) NULL,
    current_month_token_quota BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_plans_module_name UNIQUE (module, name),
    CONSTRAINT chk_plans_module CHECK (module IN ('PRODUCTS', 'ADS_ANALYZER'))
);

CREATE TABLE api_pricing_history (
    id CHAR(36) NOT NULL,
    input_price_usd_per_million DECIMAL(10, 4) NOT NULL,
    output_price_usd_per_million DECIMAL(10, 4) NOT NULL,
    effective_from DATETIME NOT NULL,
    updated_by CHAR(36) NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_api_pricing_history_updated_by FOREIGN KEY (updated_by) REFERENCES sellers (id)
);

CREATE TABLE exchange_rate_snapshots (
    id CHAR(36) NOT NULL,
    usd_brl_rate DECIMAL(10, 4) NOT NULL,
    reference_date DATE NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_exchange_rate_snapshots_reference_date UNIQUE (reference_date)
);

-- RN-PLAN-002/003: 6 fixed rows, one per module x tier. Manually
-- reviewed values, not derived at runtime. current_month_token_quota
-- starts NULL for the three ADS_ANALYZER plans — the daily exchange
-- rate job (RN-TOKEN-001/002, Fase 1.5 jobs) fills it once it runs; it
-- is never backfilled here.
INSERT INTO plans (id, module, name, stores_limit, monthly_price, target_margin, current_month_token_quota, created_at, updated_at) VALUES
    (UUID(), 'PRODUCTS', 'Básico', 1, 15.00, NULL, NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (UUID(), 'PRODUCTS', 'Intermediário', 2, 25.00, NULL, NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (UUID(), 'PRODUCTS', 'Completo', 5, 45.00, NULL, NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (UUID(), 'ADS_ANALYZER', 'Básico', 1, 39.00, 0.7300, NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (UUID(), 'ADS_ANALYZER', 'Intermediário', 2, 69.00, 0.6500, NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (UUID(), 'ADS_ANALYZER', 'Completo', 5, 149.00, 0.5400, NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP());
