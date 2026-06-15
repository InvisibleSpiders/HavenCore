package dev.invisiblespiders.haven.core.upgrade;

import java.util.Objects;
import java.util.UUID;

public record UpgradePurchaseRecord(
        long id,
        String providerId,
        String upgradeId,
        UUID beneficiaryId,
        UUID purchaserId,
        String targetScope,
        int purchasedLevel,
        String source,
        int affectedCount,
        long createdAt
) {
    public UpgradePurchaseRecord {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(upgradeId, "upgradeId");
        Objects.requireNonNull(beneficiaryId, "beneficiaryId");
        Objects.requireNonNull(purchaserId, "purchaserId");
        Objects.requireNonNull(targetScope, "targetScope");
        Objects.requireNonNull(source, "source");
    }
}
