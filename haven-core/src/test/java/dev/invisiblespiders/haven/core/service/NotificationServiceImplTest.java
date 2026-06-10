package dev.invisiblespiders.haven.core.service;

import dev.invisiblespiders.haven.core.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceImplTest {

    @Test
    void broadcastResolvesTemplateOnceForAllPlayers() {
        ConfigManager config = mock(ConfigManager.class);
        when(config.getMessage("notice.broadcast")).thenReturn("<green>Hello <player>.");
        Player first = mock(Player.class);
        Player second = mock(Player.class);
        NotificationServiceImpl notifications = new NotificationServiceImpl(config);

        notifications.broadcast(List.of(first, second), "notice.broadcast", Map.of("player", "Haven"));

        verify(config, times(1)).getMessage("notice.broadcast");
        assertEquals("Hello Haven.", sentPlainMessage(first));
        assertEquals("Hello Haven.", sentPlainMessage(second));
    }

    @Test
    void placeholderValuesAreRenderedLiterally() {
        ConfigManager config = mock(ConfigManager.class);
        when(config.getMessage("notice.player")).thenReturn("<green>Hello <player>.");
        Player player = mock(Player.class);
        NotificationServiceImpl notifications = new NotificationServiceImpl(config);

        notifications.send(player, "notice.player", Map.of("player", "<red>Haven"));

        assertEquals("Hello <red>Haven.", sentPlainMessage(player));
    }

    @Test
    void reloadRefreshesOnlyMessageTemplates() {
        ConfigManager config = mock(ConfigManager.class);
        NotificationServiceImpl notifications = new NotificationServiceImpl(config);

        notifications.reload();

        verify(config).reloadMessages();
        verify(config, times(0)).reload();
    }

    private static String sentPlainMessage(Player player) {
        ArgumentCaptor<Component> messageCaptor = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(messageCaptor.capture());
        return PlainTextComponentSerializer.plainText().serialize(messageCaptor.getValue());
    }
}
