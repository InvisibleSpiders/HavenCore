package dev.invisiblespiders.haven.api.upgrade;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record UpgradeLevel(
        int level,
        String displayName,
        List<UpgradeRequirement> requirements,
        List<UpgradeEffect> effects,
        Map<String, String> metadata
) {
    public UpgradeLevel {
        Objects.requireNonNull(displayName, "displayName");
        requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
        effects = List.copyOf(Objects.requireNonNull(effects, "effects"));
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }
}
