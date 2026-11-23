CREATE TABLE IF NOT EXISTS customer_orders (
    id BIGSERIAL PRIMARY KEY,
    customer_email VARCHAR(180) NOT NULL,
    product_id BIGINT NOT NULL,
    sku VARCHAR(64) NOT NULL,
    product_name VARCHAR(150) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10, 2) NOT NULL CHECK (unit_price >= 0),
    total_amount NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_customer_orders_status ON customer_orders (status);
CREATE INDEX IF NOT EXISTS idx_customer_orders_sku ON customer_orders (sku);