CREATE TABLE payment_attempts (
    id CHAR(36) NOT NULL,
    subscription_id CHAR(36) NOT NULL,
    type VARCHAR(20) NOT NULL,
    attempt_number INT NOT NULL,
    attempted_at DATETIME NOT NULL,
    result VARCHAR(20) NOT NULL,
    decline_reason VARCHAR(200) NULL,
    gateway_transaction_id VARCHAR(100) NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_payment_attempts_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE RESTRICT,
    CONSTRAINT chk_payment_attempts_type CHECK (type IN ('INITIAL_CHARGE', 'RENEWAL')),
    CONSTRAINT chk_payment_attempts_result CHECK (result IN ('APPROVED', 'DECLINED', 'PENDING'))
);

CREATE INDEX idx_payment_attempts_subscription_type_attempt ON payment_attempts (subscription_id, type, attempt_number);

CREATE TABLE refunds (
    id CHAR(36) NOT NULL,
    subscription_id CHAR(36) NOT NULL,
    type VARCHAR(30) NOT NULL,
    amount_brl DECIMAL(10, 2) NOT NULL,
    requested_at DATETIME NOT NULL,
    processed_at DATETIME NULL,
    gateway_transaction_id VARCHAR(100) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_refunds_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE RESTRICT,
    CONSTRAINT chk_refunds_type CHECK (type IN ('FULL_WITHDRAWAL', 'PARTIAL_COMBO_WITHDRAWAL'))
);

CREATE INDEX idx_refunds_subscription_id ON refunds (subscription_id);
