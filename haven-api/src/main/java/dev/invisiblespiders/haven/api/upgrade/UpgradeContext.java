package dev.invisiblespiders.haven.api.upgrade;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record UpgradeContext(
        /** Null for non-purchase contexts such as grants, revokes, or automated effects. */
        Player purchaser,
        UUID targetPlayerId,
        String upgradeId,
        int level,
        UpgradeScope scope,
        Map<String, String> metadata
) {
    public UpgradeContext {
        Objects.requireNonNull(targetPlayerId, "targetPlayerId");
        Objects.requireNonNull(upgradeId, "upgradeId");
        Objects.requireNonNull(scope, "scope");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }
}
