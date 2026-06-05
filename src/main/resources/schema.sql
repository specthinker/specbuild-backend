CREATE TABLE IF NOT EXISTS specs (
    id              TEXT PRIMARY KEY,
    title           TEXT NOT NULL,
    sections_json   TEXT NOT NULL,
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    version         INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_specs_updated_at ON specs(updated_at DESC);
