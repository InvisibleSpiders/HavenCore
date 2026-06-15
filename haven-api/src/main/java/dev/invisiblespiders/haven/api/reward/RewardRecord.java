package dev.invisiblespiders.haven.api.reward;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
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
        /** Null when the reward does not expire. */
        Instant expiresAt,
        /** Null until the reward has been claimed. */
        Instant claimedAt
) {
    public RewardRecord {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(rewardType, "rewardType");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(displayText, "displayText");
        payload = Map.copyOf(Objects.requireNonNull(payload, "payload"));
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
