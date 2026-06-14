package dev.invisiblespiders.haven.core.reward;

import dev.invisiblespiders.haven.api.reward.HavenRewardService;
import dev.invisiblespiders.haven.api.reward.RewardRecord;
import dev.invisiblespiders.haven.api.reward.RewardStatus;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RewardLoginListenerTest {

    @Test
    void pendingCountZeroSendsNoMessage() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        HavenRewardService rewards = mock(HavenRewardService.class);
        when(rewards.pending(player.getUniqueId())).thenReturn(List.of());
        RewardLoginListener listener = new RewardLoginListener(rewards, null);

        listener.onJoin(new PlayerJoinEvent(player, (String) null));

        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test
    void pendingCountSendsClickableRewardsReminder() {
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        HavenRewardService rewards = mock(HavenRewardService.class);
        when(rewards.pending(playerId)).thenReturn(List.of(
                reward(1L, playerId),
                reward(2L, playerId)
        ));
        ConfigManager config = mock(ConfigManager.class);
        when(config.getMessage("rewards.login-reminder")).thenReturn("");
        RewardLoginListener listener = new RewardLoginListener(rewards, config);

        listener.onJoin(new PlayerJoinEvent(player, (String) null));

        ArgumentCaptor<Component> messageCaptor = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(messageCaptor.capture());
        Component message = messageCaptor.getValue();
        String plain = PlainTextComponentSerializer.plainText().serialize(message);
        assertTrue(plain.contains("2"), "Expected reward count in: " + plain);
        assertTrue(containsRunCommand(message, "/rewards"));
    }

    @Test
    void configuredMessagePreservesConfiguredClickEvent() {
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        HavenRewardService rewards = mock(HavenRewardService.class);
        when(rewards.pending(playerId)).thenReturn(List.of(reward(1L, playerId)));
        ConfigManager config = mock(ConfigManager.class);
        when(config.getMessage("rewards.login-reminder"))
                .thenReturn("<click:run_command:'/custom-rewards'>Custom {count}</click>");
        RewardLoginListener listener = new RewardLoginListener(rewards, config);

        listener.onJoin(new PlayerJoinEvent(player, (String) null));

        ArgumentCaptor<Component> messageCaptor = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(messageCaptor.capture());
        Component message = messageCaptor.getValue();
        String plain = PlainTextComponentSerializer.plainText().serialize(message);
        assertTrue(plain.contains("1"), "Expected reward count in: " + plain);
        assertTrue(containsRunCommand(message, "/custom-rewards"));
        assertFalse(containsRunCommand(message, "/rewards"));
    }

    private static RewardRecord reward(long id, UUID playerId) {
        return new RewardRecord(id, "test", "crate-key", playerId, "Crate Key", Map.of(),
                RewardStatus.PENDING, Instant.now(), Instant.now().plusSeconds(3600), null);
    }

    private static boolean containsRunCommand(Component component, String command) {
        ClickEvent clickEvent = component.clickEvent();
        if (clickEvent != null
                && clickEvent.action() == ClickEvent.Action.RUN_COMMAND
                && command.equals(clickEvent.value())) {
            return true;
        }
        for (Component child : component.children()) {
            if (containsRunCommand(child, command)) {
                return true;
            }
        }
        return false;
    }
}
