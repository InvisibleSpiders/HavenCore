package dev.invisiblespiders.haven.api.upgrade;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public record UpgradeContext(
        Player purchaser,
        UUID targetPlayerId,
        String upgradeId,
        int level,
        UpgradeScope scope,
        Map<String, String> metadata
) {
}
