package dev.invisiblespiders.haven.core.dialog;

import dev.invisiblespiders.haven.api.upgrade.HavenUpgradeService;
import dev.invisiblespiders.haven.api.upgrade.UpgradeCategory;
import dev.invisiblespiders.haven.api.upgrade.UpgradeDefinition;
import dev.invisiblespiders.haven.api.upgrade.UpgradeLevel;
import dev.invisiblespiders.haven.api.upgrade.UpgradePurchaseResult;
import dev.invisiblespiders.haven.api.upgrade.UpgradeScope;
import dev.invisiblespiders.haven.api.upgrade.UpgradeViewRequest;
import dev.invisiblespiders.haven.api.upgrade.UpgradeVisibility;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void categoryHeadersInterleavedWithUpgradesInSortOrder() {
        HavenUpgradeService upgrades = mock(HavenUpgradeService.class);
        ConfigManager config = mock(ConfigManager.class);
        when(config.getUpgrades()).thenReturn(new YamlConfiguration());
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        UpgradeCategory storage = new UpgradeCategory("storage", "Storage", "CHEST", 0);
        UpgradeCategory misc    = new UpgradeCategory("misc",    "Misc",    "BOOK",  1);

        UpgradeDefinition slotsDef = new UpgradeDefinition(
                "core:slots", "core", storage, UpgradeScope.PLAYER, UpgradeVisibility.VISIBLE,
                null, List.of(new UpgradeLevel(1, "Slot Upgrade", List.of(), List.of(), Map.of()))
        );
        UpgradeDefinition rangeDef = new UpgradeDefinition(
                "core:range", "core", misc, UpgradeScope.PLAYER, UpgradeVisibility.VISIBLE,
                null, List.of(new UpgradeLevel(1, "Range Upgrade", List.of(), List.of(), Map.of()))
        );

        when(upgrades.currentLevel(playerId, "core:slots")).thenReturn(0);
        when(upgrades.currentLevel(playerId, "core:range")).thenReturn(0);

        UpgradeDialog dialog = new UpgradeDialog(config, upgrades);
        List<Component> rows = dialog.buildBodyComponents(
                player,
                UpgradeViewRequest.all(),
                List.of(slotsDef, rangeDef),
                Map.of()
        );

        // Expected: [Storage header, slots entry, Misc header, range entry]
        assertEquals(4, rows.size());
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        assertTrue(plain.serialize(rows.get(0)).contains("Storage"), "row 0 should be Storage header");
        assertTrue(plain.serialize(rows.get(1)).contains("Slot Upgrade"),  "row 1 should be slots entry");
        assertTrue(plain.serialize(rows.get(2)).contains("Misc"),    "row 2 should be Misc header");
        assertTrue(plain.serialize(rows.get(3)).contains("Range Upgrade"), "row 3 should be range entry");
    }
}
