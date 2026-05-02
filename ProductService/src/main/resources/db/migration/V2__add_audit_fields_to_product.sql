-- ─────────────────────────────────────────────────────────────────────────────
-- V2 : Add audit timestamp columns to product table
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE product
    ADD COLUMN created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    ADD COLUMN updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now();
