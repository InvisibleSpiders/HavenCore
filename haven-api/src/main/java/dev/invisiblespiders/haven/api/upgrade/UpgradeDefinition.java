package dev.invisiblespiders.haven.api.upgrade;

import java.util.List;

public record UpgradeDefinition(
        String id,
        String providerId,
        UpgradeCategory category,
        UpgradeScope scope,
        UpgradeVisibility visibility,
        String permission,
        List<UpgradeLevel> levels
) {
}
