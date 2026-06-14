package dev.invisiblespiders.haven.api.upgrade;

import java.util.List;
import java.util.Map;

public record UpgradeLevel(
        int level,
        String displayName,
        List<UpgradeRequirement> requirements,
        List<UpgradeEffect> effects,
        Map<String, String> metadata
) {
}
