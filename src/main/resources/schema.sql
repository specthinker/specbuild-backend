CREATE TABLE IF NOT EXISTS specs (
    id              TEXT PRIMARY KEY,
    title           TEXT NOT NULL,
    sections_json   TEXT NOT NULL,
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    version         INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_specs_updated_at ON specs(updated_at DESC);

CREATE TABLE IF NOT EXISTS users (
    id                   TEXT PRIMARY KEY,
    email                TEXT UNIQUE,
    plan                 TEXT NOT NULL DEFAULT 'free',
    plan_set_at          TEXT NOT NULL,
    period_start         TEXT NOT NULL,
    specs_used           INTEGER NOT NULL DEFAULT 0,
    polish_used          INTEGER NOT NULL DEFAULT 0,
    stripe_customer_id   TEXT UNIQUE,
    created_at           TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_stripe ON users(stripe_customer_id);

CREATE TABLE IF NOT EXISTS oauth_accounts (
    provider           TEXT NOT NULL,
    provider_subject   TEXT NOT NULL,
    user_id            TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at         TEXT NOT NULL,
    PRIMARY KEY (provider, provider_subject)
);

CREATE INDEX IF NOT EXISTS idx_oauth_user ON oauth_accounts(user_id);

CREATE TABLE IF NOT EXISTS sessions (
    id            TEXT PRIMARY KEY,
    user_id       TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at    TEXT NOT NULL,
    last_seen_at  TEXT NOT NULL,
    expires_at    TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_exp  ON sessions(expires_at);

CREATE TABLE IF NOT EXISTS magic_link_tokens (
    token_hash    TEXT PRIMARY KEY,
    email         TEXT NOT NULL,
    redirect_url  TEXT NOT NULL,
    client_id     TEXT,
    expires_at    TEXT NOT NULL,
    used_at       TEXT
);

CREATE INDEX IF NOT EXISTS idx_magic_exp ON magic_link_tokens(expires_at);

CREATE TABLE IF NOT EXISTS oauth_state_tokens (
    state_hash     TEXT PRIMARY KEY,
    pkce_verifier  TEXT NOT NULL,
    redirect_url   TEXT NOT NULL,
    expires_at     TEXT NOT NULL,
    used_at        TEXT
);

CREATE INDEX IF NOT EXISTS idx_state_exp ON oauth_state_tokens(expires_at);

CREATE TABLE IF NOT EXISTS processed_stripe_events (
    event_id    TEXT PRIMARY KEY,
    received_at TEXT NOT NULL
);
