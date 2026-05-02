-- V1__init_schema.sql
-- Initial schema: users and sessions tables

CREATE TABLE IF NOT EXISTS users (
    id       BIGSERIAL    PRIMARY KEY,
    name     VARCHAR(255) NOT NULL,
    email    VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
    id      BIGSERIAL    PRIMARY KEY,
    token   VARCHAR(512) NOT NULL UNIQUE,
    user_id BIGINT       NOT NULL REFERENCES users(id),
    status  VARCHAR(50)  NOT NULL
);