-- ─────────────────────────────────────────────────────────────────────────────
-- V1 : Create the initial products table
--
-- Naming convention used by Hibernate's SpringPhysicalNamingStrategy:
--   imageUrl  →  image_url   (camelCase → snake_case, auto-mapped by JPA)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS product (
    id          BIGSERIAL        PRIMARY KEY,
    title       VARCHAR(255),
    description TEXT,
    price       DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    category    VARCHAR(255),
    image_url   VARCHAR(255)
);
