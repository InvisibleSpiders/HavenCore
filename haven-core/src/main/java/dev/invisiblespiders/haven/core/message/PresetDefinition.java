package dev.invisiblespiders.haven.core.message;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public record PresetDefinition(
        String id,
        String message,
        UnlockType unlockType,
        @Nullable String codexMilestone,
        @Nullable String permission,
        boolean isDefault
) {
    public boolean isAvailableByPermission(Player player) {
        return switch (unlockType) {
            case FREE -> true;
            case PERMISSION -> permission != null && player.hasPermission(permission);
            case CODEX, ADMIN -> false;
        };
    }
}
