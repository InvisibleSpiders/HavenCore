CREATE TABLE IF NOT EXISTS haven_rewards (
    id BIGINT PRIMARY KEY,
    provider_id VARCHAR(64) NOT NULL,
    reward_type VARCHAR(128) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    display_text VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT,
    claimed_at BIGINT,
    source VARCHAR(128) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_haven_rewards_player_status
    ON haven_rewards (player_uuid, status);

CREATE INDEX idx_haven_rewards_expiry
    ON haven_rewards (expires_at);
