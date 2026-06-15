package dev.invisiblespiders.haven.api.upgrade;

import java.util.Objects;

public record UpgradeCategory(String id, String displayName, String icon, int sortOrder) {

    public UpgradeCategory {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(icon, "icon");
    }
}
