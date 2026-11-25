CREATE TABLE IF NOT EXISTS user_accounts (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(180) NOT NULL UNIQUE,
    full_name VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255),
    auth_provider VARCHAR(32) NOT NULL,
    google_sub VARCHAR(128) UNIQUE,
    role VARCHAR(32) NOT NULL DEFAULT 'CUSTOMER',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_accounts_email ON user_accounts (email);
CREATE INDEX IF NOT EXISTS idx_user_accounts_google_sub ON user_accounts (google_sub);