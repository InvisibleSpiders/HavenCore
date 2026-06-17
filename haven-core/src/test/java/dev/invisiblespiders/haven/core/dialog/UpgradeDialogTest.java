package dev.invisiblespiders.haven.core.dialog;

import dev.invisiblespiders.haven.api.upgrade.HavenUpgradeService;
import dev.invisiblespiders.haven.api.upgrade.UpgradeCategory;
import dev.invisiblespiders.haven.api.upgrade.UpgradeDefinition;
import dev.invisiblespiders.haven.api.upgrade.UpgradeLevel;
import dev.invisiblespiders.haven.api.upgrade.UpgradePurchaseResult;
import dev.invisiblespiders.haven.api.upgrade.UpgradeScope;
import dev.invisiblespiders.haven.api.upgrade.UpgradeVisibility;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpgradeDialogTest {

    @Test
    void staleConfirmationDoesNotPurchaseDifferentLevel() {
        HavenUpgradeService upgrades = mock(HavenUpgradeService.class);
        ConfigManager config = mock(ConfigManager.class);
        when(config.getUpgrades()).thenReturn(new YamlConfiguration());
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        UpgradeDefinition definition = new UpgradeDefinition(
                "test:slots",
                "test",
                new UpgradeCategory("storage", "Storage", "CHEST", 0),
                UpgradeScope.PLAYER,
                UpgradeVisibility.VISIBLE,
                null,
                List.of(new UpgradeLevel(1, "Level 1", List.of(), List.of(), Map.of()))
        );
        when(upgrades.currentLevel(playerId, "test:slots")).thenReturn(1);
        UpgradeDialog dialog = new UpgradeDialog(config, upgrades);

        UpgradePurchaseResult result = dialog.purchaseIfLevelUnchanged(player, definition, 0);

        assertFalse(result.succeeded());
        assertEquals("stale-upgrade-dialog", result.code());
        verify(upgrades, never()).purchase(player, "test:slots", UpgradeScope.PLAYER);
    }

    @Test
    void buttonLayoutEntriesInterleaveCategoryHeadersWithTheirUpgrades() {
        UpgradeCategory bank = new UpgradeCategory("bank", "Bank", "EMERALD", 20);
        UpgradeCategory claims = new UpgradeCategory("claims", "Claims", "GRASS_BLOCK", 40);
        UpgradeCategory personal = new UpgradeCategory("personal", "Personal", "CLOCK", 10);
        List<UpgradeDefinition> definitions = List.of(
                definition("test:claim-limit", claims),
                definition("test:afk-timer", personal),
                definition("test:bank-cap", bank),
                definition("test:home-slots", personal)
        );

        List<String> entries = UpgradeDialog.buttonLayoutEntries(definitions, true).stream()
                .map(entry -> entry.header()
                        ? "header:" + entry.category().displayName()
                        : "upgrade:" + entry.definition().id())
                .toList();

        assertThat(entries).containsExactly(
                "header:Personal",
                "upgrade:test:afk-timer",
                "upgrade:test:home-slots",
                "header:Bank",
                "upgrade:test:bank-cap",
                "header:Claims",
                "upgrade:test:claim-limit"
        );
    }

    private static UpgradeDefinition definition(String id, UpgradeCategory category) {
        return new UpgradeDefinition(
                id,
                "test",
                category,
                UpgradeScope.PLAYER,
                UpgradeVisibility.VISIBLE,
                null,
                List.of(new UpgradeLevel(1, "Level 1", List.of(), List.of(), Map.of()))
        );
    }
}
