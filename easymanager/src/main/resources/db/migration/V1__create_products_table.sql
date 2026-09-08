-- Bootstraps the schema exactly as it exists today, created previously by
-- Hibernate's ddl-auto=update. Uses the native UUID column type (not the
-- CHAR(36) convention adopted from V2 onward, see
-- docs/easy-manager-arquitetura-banco-de-dados.md sec. 1) because that is
-- what ddl-auto actually generated for products.id.
CREATE TABLE products (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    amount INT NOT NULL,
    photo_url VARCHAR(255) NOT NULL,
    barcode VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);
