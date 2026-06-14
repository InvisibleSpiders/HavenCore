package dev.invisiblespiders.haven.api.upgrade;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface UpgradeProvider {

    String id();

    String displayName();

    List<UpgradeCategory> categories();

    List<UpgradeDefinition> definitions();

    Optional<UpgradeEffect> effect(String type);

    Optional<UpgradeRequirement> requirement(String type, Map<String, String> values);
}
