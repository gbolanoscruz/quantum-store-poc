CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    price NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO products (sku, name, description, price)
VALUES
    ('QTM-CPU-001', 'Quantum Core X1', 'Next-gen modular compute unit for edge workloads', 299.99),
    ('QTM-KEY-002', 'Mechanical Dev Keyboard', 'Low-profile tactile wireless keyboard', 129.50),
    ('QTM-MON-003', 'UltraWide 34" Studio Display', '144Hz USB-C productivity monitor', 549.00)
ON CONFLICT (sku) DO NOTHING;