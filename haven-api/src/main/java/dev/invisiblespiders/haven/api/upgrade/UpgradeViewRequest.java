package dev.invisiblespiders.haven.api.upgrade;

import java.util.Objects;
import java.util.Set;

public record UpgradeViewRequest(Set<String> providerIds, Set<String> categoryIds) {

    public UpgradeViewRequest {
        providerIds = Set.copyOf(Objects.requireNonNull(providerIds, "providerIds"));
        categoryIds = Set.copyOf(Objects.requireNonNull(categoryIds, "categoryIds"));
    }

    public static UpgradeViewRequest all() {
        return new UpgradeViewRequest(Set.of(), Set.of());
    }

    public static UpgradeViewRequest categories(Set<String> categoryIds) {
        return new UpgradeViewRequest(Set.of(), categoryIds);
    }

    public static UpgradeViewRequest providers(Set<String> providerIds) {
        return new UpgradeViewRequest(providerIds, Set.of());
    }
}
