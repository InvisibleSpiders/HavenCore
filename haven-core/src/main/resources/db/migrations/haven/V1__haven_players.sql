CREATE TABLE IF NOT EXISTS haven_players (
    uuid       TEXT NOT NULL PRIMARY KEY,
    name_cache TEXT NOT NULL,
    first_seen INTEGER NOT NULL,
    last_seen  INTEGER NOT NULL
);
