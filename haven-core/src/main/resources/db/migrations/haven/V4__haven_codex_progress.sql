CREATE TABLE IF NOT EXISTS haven_codex_progress (
    player_uuid   TEXT    NOT NULL,
    entry_key     TEXT    NOT NULL,
    discovered_at INTEGER NOT NULL,
    PRIMARY KEY (player_uuid, entry_key)
);

CREATE INDEX IF NOT EXISTS idx_codex_player ON haven_codex_progress(player_uuid);
