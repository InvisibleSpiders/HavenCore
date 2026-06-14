package dev.invisiblespiders.haven.core.command;

import dev.invisiblespiders.haven.api.reward.HavenRewardService;
import dev.invisiblespiders.haven.api.reward.RewardRecord;
import dev.invisiblespiders.haven.api.reward.RewardStatus;
import dev.invisiblespiders.haven.api.service.HavenHookRegistry;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import dev.invisiblespiders.haven.core.dialog.RewardDialog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RewardCommandTest {

    @Test
    void rewardsRejectsConsoleSender() {
        ConfigManager config = configWithMessages();
        RewardDialog dialog = mock(RewardDialog.class);
        CommandSender sender = mock(CommandSender.class);

        new RewardsCommand(config, dialog)
                .onCommand(sender, mock(Command.class), "rewards", new String[0]);

        verify(dialog, never()).open(any());
        assertTrue(sentPlainMessages(sender).stream().anyMatch(message -> message.contains("players only")));
    }

    @Test
    void rewardsOpensRewardDialogForPlayers() {
        ConfigManager config = configWithMessages();
        RewardDialog dialog = mock(RewardDialog.class);
        Player player = mock(Player.class);
        when(player.hasPermission("haven.rewards")).thenReturn(true);

        new RewardsCommand(config, dialog)
                .onCommand(player, mock(Command.class), "rewards", new String[0]);

        verify(dialog).open(player);
    }

    @Test
    void havenRewardsListRequiresAdminPermission() {
        ConfigManager config = configWithMessages();
        HavenRewardService rewards = mock(HavenRewardService.class);
        Plugin plugin = mock(Plugin.class);
        HavenHookRegistry hooks = mock(HavenHookRegistry.class);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("haven.admin.rewards")).thenReturn(false);

        HavenCommand command = new HavenCommand(plugin, config, hooks, null, null, null,
                null, new RewardAdminCommand(config, rewards, name -> offlinePlayer(name)));

        command.onCommand(sender, mock(Command.class), "haven",
                new String[] {"rewards", "list", "Nick"});

        verify(rewards, never()).pending(any(UUID.class));
        assertTrue(sentPlainMessages(sender).stream().anyMatch(message -> message.contains("No permission")));
    }

    @Test
    void havenRewardsRevokeRejectsRewardOutsideTargetMailbox() {
        ConfigManager config = configWithMessages();
        HavenRewardService rewards = mock(HavenRewardService.class);
        Plugin plugin = mock(Plugin.class);
        HavenHookRegistry hooks = mock(HavenHookRegistry.class);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("haven.admin.rewards")).thenReturn(true);
        OfflinePlayer alice = offlinePlayer("Alice");
        UUID aliceId = alice.getUniqueId();
        when(rewards.pending(aliceId)).thenReturn(List.of(reward(9L, aliceId)));

        HavenCommand command = new HavenCommand(plugin, config, hooks, null, null, null,
                null, new RewardAdminCommand(config, rewards, name -> alice));

        command.onCommand(sender, mock(Command.class), "haven",
                new String[] {"rewards", "revoke", "Alice", "123"});

        verify(rewards, never()).revoke(eq(123L), any());
        assertTrue(sentPlainMessages(sender).stream()
                .anyMatch(message -> message.contains("does not belong")));
    }

    private static OfflinePlayer offlinePlayer(String name) {
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes()));
        return player;
    }

    private static RewardRecord reward(long id, UUID playerId) {
        return new RewardRecord(id, "test", "crate-key", playerId, "Crate Key", Map.of(),
                RewardStatus.PENDING, Instant.now(), null, null);
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
