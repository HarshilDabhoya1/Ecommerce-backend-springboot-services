-- ─────────────────────────────────────────────────────────────────────────────
-- V3 : Add soft-delete column to product table
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE product
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE NULL;
