package dev.invisiblespiders.haven.api.upgrade;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HavenUpgradeService {

    void registerProvider(UpgradeProvider provider);

    void unregisterProvider(String providerId);

    List<UpgradeProvider> providers();

    List<UpgradeDefinition> definitions();

    Optional<UpgradeDefinition> findDefinition(String upgradeId);

    int currentLevel(UUID playerId, String upgradeId);

    UpgradePurchaseResult purchase(Player purchaser, String upgradeId);

    UpgradePurchaseResult purchase(Player purchaser, String upgradeId, UpgradeScope scope);

    UpgradePurchaseResult grant(UUID targetPlayerId, String upgradeId, int level, String source);

    UpgradePurchaseResult revoke(UUID targetPlayerId, String upgradeId, String source);

    void openDialog(Player player, UpgradeViewRequest request);
}
