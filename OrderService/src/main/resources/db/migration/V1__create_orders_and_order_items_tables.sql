-- ─────────────────────────────────────────────────────────────────────────────
-- V1 : Create the orders and order_items tables
--
-- Notes:
--   - "order" is a reserved SQL keyword, so the table is named "orders"
--   - user_id and product_id are cross-service references (no FK constraint)
--   - order_status stores the enum value as a string for readability
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS orders (
    id           BIGSERIAL        PRIMARY KEY,
    user_id      BIGINT           NOT NULL,
    user_email   VARCHAR(255)     NOT NULL,
    status       VARCHAR(50)      NOT NULL DEFAULT 'PENDING',
    total_amount DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    created_at   TIMESTAMPTZ      NOT NULL,
    updated_at   TIMESTAMPTZ      NOT NULL
);

CREATE TABLE IF NOT EXISTS order_items (
    id            BIGSERIAL        PRIMARY KEY,
    order_id      BIGINT           NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id    BIGINT           NOT NULL,
    product_title VARCHAR(255)     NOT NULL,
    quantity      INT              NOT NULL CHECK (quantity > 0),
    unit_price    DOUBLE PRECISION NOT NULL CHECK (unit_price >= 0)
);

-- Index to make "all orders for a user" queries fast
CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders(user_id);

-- Index to make "all items for an order" queries fast
CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);
