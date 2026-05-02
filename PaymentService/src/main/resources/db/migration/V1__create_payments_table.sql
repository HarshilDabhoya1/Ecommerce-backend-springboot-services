-- V1__create_payments_table.sql
-- Creates the payments table for PaymentService.

CREATE TABLE IF NOT EXISTS payments
(
    id             BIGSERIAL PRIMARY KEY,
    order_id       BIGINT         NOT NULL,
    user_id        BIGINT         NOT NULL,
    user_email     VARCHAR(255)   NOT NULL,
    amount         DOUBLE PRECISION NOT NULL,
    currency       VARCHAR(10)    NOT NULL DEFAULT 'INR',
    payment_method VARCHAR(30)    NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    transaction_id VARCHAR(255),
    failure_reason TEXT,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- Index for fetching all payments for a given order quickly
CREATE INDEX IF NOT EXISTS idx_payments_order_id ON payments (order_id);

-- Index for fetching payment history for a user quickly
CREATE INDEX IF NOT EXISTS idx_payments_user_id ON payments (user_id);

-- Index for status-based queries (e.g., find all PENDING payments)
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments (status);
