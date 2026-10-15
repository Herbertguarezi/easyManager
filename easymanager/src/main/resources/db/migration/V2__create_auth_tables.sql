CREATE TABLE sellers (
    id CHAR(36) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    email_confirmed_at DATETIME NULL,
    cnpj CHAR(14) NULL,
    cnpj_verified_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_sellers_email UNIQUE (email),
    CONSTRAINT uq_sellers_cnpj UNIQUE (cnpj),
    CONSTRAINT chk_sellers_role CHECK (role IN ('ADMIN', 'SELLER')),
    CONSTRAINT chk_sellers_status CHECK (status IN ('PENDING', 'ACTIVE', 'INACTIVE'))
);

CREATE TABLE refresh_tokens (
    id CHAR(36) NOT NULL,
    seller_id CHAR(36) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    replaced_by CHAR(36) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_refresh_tokens_seller FOREIGN KEY (seller_id) REFERENCES sellers (id),
    CONSTRAINT fk_refresh_tokens_replaced_by FOREIGN KEY (replaced_by) REFERENCES refresh_tokens (id)
);

CREATE INDEX idx_refresh_tokens_seller_id ON refresh_tokens (seller_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);

CREATE TABLE email_confirmation_tokens (
    id CHAR(36) NOT NULL,
    seller_id CHAR(36) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_email_confirmation_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_email_confirmation_tokens_seller FOREIGN KEY (seller_id) REFERENCES sellers (id)
);

CREATE TABLE password_reset_tokens (
    id CHAR(36) NOT NULL,
    seller_id CHAR(36) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_seller FOREIGN KEY (seller_id) REFERENCES sellers (id)
);
