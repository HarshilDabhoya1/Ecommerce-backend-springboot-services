-- ─────────────────────────────────────────────────────────────────────────────
-- V3 : Add role column to users table
--
-- Default is 'USER' so all existing users keep normal access.
-- To promote a user to admin, run:
--   UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS role VARCHAR(50) NOT NULL DEFAULT 'USER';
