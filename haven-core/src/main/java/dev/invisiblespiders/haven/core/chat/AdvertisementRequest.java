package dev.invisiblespiders.haven.core.chat;

import java.util.UUID;

public record AdvertisementRequest(
        UUID playerUuid,
        String playerName,
        String shopWarpName,
        String message,
        long submittedAt
) {}
