CREATE TABLE images
(
    id                TEXT PRIMARY KEY,
    status            TEXT NOT NULL,
    original_key      TEXT NOT NULL,
    original_clean_key TEXT,
    small_key         TEXT,
    medium_key        TEXT,
    large_key         TEXT,
    mime_type         TEXT NOT NULL,
    original_width    INTEGER,
    original_height   INTEGER,
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    error_message     TEXT
);
