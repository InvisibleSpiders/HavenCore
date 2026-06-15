package dev.invisiblespiders.haven.core.afk;

import dev.invisiblespiders.haven.api.model.HavenPlayer;
import dev.invisiblespiders.haven.api.service.HavenPlayerService;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.logging.Logger;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AfkManagerTest {

    @Mock Plugin plugin;
    @Mock Server server;
    @Mock HavenPlayerService playerService;
    @Mock Player player;

    AfkSettings settings;
    AfkManager manager;
    UUID uuid;

    @BeforeEach
    void setup() {
        uuid = UUID.randomUUID();
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        settings = AfkSettings.from(new org.bukkit.configuration.file.YamlConfiguration());
        manager = new AfkManager(settings, plugin, playerService);
    }

    @Test
    void notAfkByDefault() {
        assertThat(manager.isAfk(uuid)).isFalse();
    }

    @Test
    void idleSecondsZeroWhenNotTracked() {
        assertThat(manager.getIdleSeconds(uuid)).isEqualTo(0L);
    }

    @Test
    void recordActivityInitialisesLastActivity() {
        when(player.getUniqueId()).thenReturn(uuid);
        manager.recordActivity(player);
        assertThat(manager.getIdleSeconds(uuid)).isLessThan(2L);
    }

    @Test
    void setAfkTrue_marksPlayerAfk() {
        manager.setAfk(uuid, true);
        assertThat(manager.isAfk(uuid)).isTrue();
    }

    @Test
    void setAfkFalse_clearsAfkState() {
        manager.setAfk(uuid, true);
        manager.setAfk(uuid, false);
        assertThat(manager.isAfk(uuid)).isFalse();
    }

    @Test
    void onQuit_removesTracking() {
        when(player.getUniqueId()).thenReturn(uuid);
        manager.recordActivity(player);
        manager.setAfk(uuid, true);
        manager.onQuit(uuid);
        assertThat(manager.isAfk(uuid)).isFalse();
        assertThat(manager.getIdleSeconds(uuid)).isEqualTo(0L);
    }
}
