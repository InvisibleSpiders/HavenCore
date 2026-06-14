CREATE TABLE IF NOT EXISTS haven_upgrade_levels (
    player_uuid VARCHAR(36) NOT NULL,
    upgrade_id VARCHAR(128) NOT NULL,
    provider_id VARCHAR(64) NOT NULL,
    current_level INTEGER NOT NULL,
    target_scope VARCHAR(64) NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, upgrade_id)
);

CREATE TABLE IF NOT EXISTS haven_upgrade_purchases (
    id BIGINT PRIMARY KEY,
    provider_id VARCHAR(64) NOT NULL,
    upgrade_id VARCHAR(128) NOT NULL,
    beneficiary_uuid VARCHAR(36),
    purchaser_uuid VARCHAR(36) NOT NULL,
    target_scope VARCHAR(64) NOT NULL,
    purchased_level INTEGER NOT NULL,
    source VARCHAR(128) NOT NULL,
    affected_count INTEGER NOT NULL DEFAULT 1,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_haven_upgrade_purchases_beneficiary
    ON haven_upgrade_purchases (beneficiary_uuid);

CREATE INDEX IF NOT EXISTS idx_haven_upgrade_purchases_upgrade
    ON haven_upgrade_purchases (upgrade_id);
