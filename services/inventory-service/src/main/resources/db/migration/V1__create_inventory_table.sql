CREATE TABLE IF NOT EXISTS inventory_items (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE,
    sku VARCHAR(64) NOT NULL UNIQUE,
    available_quantity INTEGER NOT NULL CHECK (available_quantity >= 0),
    reserved_quantity INTEGER NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO inventory_items (product_id, sku, available_quantity, reserved_quantity)
VALUES
    (1, 'QTM-CPU-001', 25, 0),
    (2, 'QTM-KEY-002', 40, 0),
    (3, 'QTM-MON-003', 12, 0)
ON CONFLICT (sku) DO NOTHING;