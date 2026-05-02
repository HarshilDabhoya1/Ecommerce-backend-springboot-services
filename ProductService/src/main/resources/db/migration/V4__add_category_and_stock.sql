-- ─────────────────────────────────────────────────────────────────────────────
-- V4 : Introduce Category table, add category FK + stock_quantity to product,
--      and drop the old free-text category column
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS category (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT
);

ALTER TABLE product
    ADD COLUMN category_id   BIGINT REFERENCES category(id),
    ADD COLUMN stock_quantity INT    NOT NULL DEFAULT 0,
    DROP COLUMN category;