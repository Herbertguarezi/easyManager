CREATE TABLE trial_contestations (
    id CHAR(36) NOT NULL,
    subscription_id CHAR(36) NOT NULL,
    justification TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by CHAR(36) NULL,
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_trial_contestations_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_trial_contestations_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES sellers (id) ON DELETE SET NULL,
    CONSTRAINT chk_trial_contestations_status CHECK (status IN ('PENDING', 'APPROVED', 'DENIED'))
);

CREATE INDEX idx_trial_contestations_subscription_id ON trial_contestations (subscription_id);
CREATE INDEX idx_trial_contestations_status ON trial_contestations (status);

CREATE TABLE risk_signals (
    id CHAR(36) NOT NULL,
    subscription_id CHAR(36) NOT NULL,
    signal_type VARCHAR(30) NOT NULL,
    detail VARCHAR(255) NULL,
    detected_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_risk_signals_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE RESTRICT,
    CONSTRAINT chk_risk_signals_signal_type CHECK (signal_type IN (
        'DEVICE_FINGERPRINT', 'DUPLICATE_EMAIL', 'DUPLICATE_PHONE', 'IP_NETWORK', 'DEVICE_LIMIT'))
);

CREATE INDEX idx_risk_signals_subscription_id ON risk_signals (subscription_id);
CREATE INDEX idx_risk_signals_signal_type ON risk_signals (signal_type);
