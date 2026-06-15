package dev.invisiblespiders.haven.api.reward;

import java.util.Map;
import java.util.Objects;

public record RewardDefinition(
        String providerId,
        String rewardType,
        String displayText,
        Map<String, String> metadata
) {
    public RewardDefinition {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(rewardType, "rewardType");
        Objects.requireNonNull(displayText, "displayText");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }
}
