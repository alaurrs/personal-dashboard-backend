-- V1__create_user_table.sql

CREATE TABLE IF NOT EXISTS users (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     email VARCHAR(255) UNIQUE NOT NULL,
                                     password_hash TEXT NOT NULL,
                                     full_name VARCHAR(255),
                                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
