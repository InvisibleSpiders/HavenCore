package dev.invisiblespiders.haven.api.chat;

import org.jetbrains.annotations.Nullable;

public record ChatChannel(
        String id,
        String displayName,
        String format,
        ChatRecipientType recipientType,
        @Nullable String permission,
        int radiusBlocks,
        int cooldownSeconds,
        @Nullable String triggerPrefix,
        boolean isDefault
) {
    /** True if this channel uses a trigger prefix instead of requiring /channel to switch. */
    public boolean hasTriggerPrefix() { return triggerPrefix != null && !triggerPrefix.isBlank(); }
}
