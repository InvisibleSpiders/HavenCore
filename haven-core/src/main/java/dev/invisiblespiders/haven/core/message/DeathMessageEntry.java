package dev.invisiblespiders.haven.core.message;

import org.jetbrains.annotations.Nullable;

public record DeathMessageEntry(
        String message,
        @Nullable String permission
) {
    public boolean isUnrestricted() { return permission == null || permission.isBlank(); }
}
