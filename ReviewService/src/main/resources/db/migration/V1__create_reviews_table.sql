-- ─────────────────────────────────────────────────────────────────────────────
-- V1 : Create reviews table
-- product_id references the ProductService — no DB-level FK across services
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS review (
    id            BIGSERIAL    PRIMARY KEY,
    product_id    BIGINT       NOT NULL,
    rating        INT          NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment       TEXT,
    reviewer_name VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);