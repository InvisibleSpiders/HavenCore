package dev.invisiblespiders.haven.api.reward;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RewardRecord(
        long id,
        String providerId,
        String rewardType,
        UUID playerId,
        String displayText,
        Map<String, String> payload,
        RewardStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant claimedAt
) {
}
