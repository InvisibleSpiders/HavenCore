CREATE TABLE IF NOT EXISTS haven_player_data (
    player_uuid TEXT    NOT NULL,
    plugin_id   TEXT    NOT NULL,
    data_key    TEXT    NOT NULL,
    value       TEXT    NOT NULL,
    PRIMARY KEY (player_uuid, plugin_id, data_key),
    FOREIGN KEY (player_uuid) REFERENCES haven_players(uuid) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_player_data_player ON haven_player_data(player_uuid);
