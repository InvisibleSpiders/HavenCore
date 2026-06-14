package dev.invisiblespiders.haven.core.command;

import dev.invisiblespiders.haven.api.service.HavenHookRegistry;
import dev.invisiblespiders.haven.api.upgrade.HavenUpgradeService;
import dev.invisiblespiders.haven.api.upgrade.UpgradeViewRequest;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpgradeCommandTest {

    @Test
    void upgradesRejectsConsoleSender() {
        ConfigManager config = configWithMessages();
        HavenUpgradeService upgrades = mock(HavenUpgradeService.class);
        CommandSender sender = mock(CommandSender.class);

        new UpgradesCommand(config, upgrades)
                .onCommand(sender, mock(Command.class), "upgrades", new String[0]);

        verify(upgrades, never()).openDialog(any(), any());
        assertTrue(sentPlainMessages(sender).stream().anyMatch(message -> message.contains("players only")));
    }

    @Test
    void upgradesOpensUpgradeDialogForPlayers() {
        ConfigManager config = configWithMessages();
        HavenUpgradeService upgrades = mock(HavenUpgradeService.class);
        Player player = mock(Player.class);
        when(player.hasPermission("haven.upgrades")).thenReturn(true);

        new UpgradesCommand(config, upgrades)
                .onCommand(player, mock(Command.class), "upgrades", new String[0]);

        verify(upgrades).openDialog(player, UpgradeViewRequest.all());
    }

    @Test
    void havenUpgradesListRequiresAdminPermission() {
        ConfigManager config = configWithMessages();
        HavenUpgradeService upgrades = mock(HavenUpgradeService.class);
        Plugin plugin = mock(Plugin.class);
        HavenHookRegistry hooks = mock(HavenHookRegistry.class);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("haven.admin.upgrades")).thenReturn(false);

        HavenCommand command = new HavenCommand(plugin, config, hooks, null, null, null,
                new UpgradeAdminCommand(config, upgrades, name -> offlinePlayer(name)), null);

        command.onCommand(sender, mock(Command.class), "haven",
                new String[] {"upgrades", "list", "Nick"});

        verify(upgrades, never()).currentLevel(any(UUID.class), any());
        assertTrue(sentPlainMessages(sender).stream().anyMatch(message -> message.contains("No permission")));
    }

    private static OfflinePlayer offlinePlayer(String name) {
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes()));
        return player;
    }

    private static ConfigManager configWithMessages() {
        ConfigManager config = mock(ConfigManager.class);
        when(config.getMessage("haven.no-permission")).thenReturn("<red>No permission.");
        return config;
    }

    private static List<String> sentPlainMessages(CommandSender sender) {
        ArgumentCaptor<Component> messageCaptor = ArgumentCaptor.forClass(Component.class);
        verify(sender).sendMessage(messageCaptor.capture());
        List<String> messages = new ArrayList<>();
        for (Component component : messageCaptor.getAllValues()) {
            messages.add(PlainTextComponentSerializer.plainText().serialize(component));
        }
        return messages;
    }
}
