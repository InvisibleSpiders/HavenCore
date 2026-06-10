package dev.invisiblespiders.haven.core.service;

import dev.invisiblespiders.haven.core.config.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceImplTest {

    @Test
    void broadcastResolvesTemplateOnceForSharedMessage() {
        ConfigManager config = mock(ConfigManager.class);
        when(config.getMessage("alerts.actionbar")).thenReturn("<green><player> joined");
        NotificationServiceImpl service = new NotificationServiceImpl(config);
        Player first = mock(Player.class);
        Player second = mock(Player.class);

        service.broadcast(
            List.of(first, second),
            "alerts.actionbar",
            Map.of("player", "InvisibleSpiders")
        );

        verify(config).getMessage("alerts.actionbar");
        verify(first).sendActionBar(any(Component.class));
        verify(second).sendActionBar(any(Component.class));
    }
}
