package dev.invisiblespiders.haven.api.reward;

import java.util.Map;

public record RewardDefinition(
        String providerId,
        String rewardType,
        String displayText,
        Map<String, String> metadata
) {
}
