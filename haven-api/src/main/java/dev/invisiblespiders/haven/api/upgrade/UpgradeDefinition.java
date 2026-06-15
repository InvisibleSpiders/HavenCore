package dev.invisiblespiders.haven.api.upgrade;

import java.util.List;
import java.util.Objects;

public record UpgradeDefinition(
        String id,
        String providerId,
        UpgradeCategory category,
        UpgradeScope scope,
        UpgradeVisibility visibility,
        /** Null when no permission gate applies. */
        String permission,
        List<UpgradeLevel> levels
) {
    public UpgradeDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(visibility, "visibility");
        levels = List.copyOf(Objects.requireNonNull(levels, "levels"));
    }
}
