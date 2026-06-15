package dev.invisiblespiders.haven.core.tab;

import dev.invisiblespiders.haven.api.service.HavenAfkService;
import dev.invisiblespiders.haven.core.hook.PlaceholderAPIHook;
import dev.invisiblespiders.haven.core.util.GroupResolver;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TabManagerTest {

    @Mock Plugin plugin;
    @Mock HavenAfkService afkService;
    @Mock GroupResolver groupResolver;
    @Mock PlaceholderAPIHook papiHook;
    @Mock Player player;

    TabSettings settings;
    TabManager manager;
    UUID uuid;

    private static YamlConfiguration load(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try { config.loadFromString(yaml); }
        catch (InvalidConfigurationException e) { throw new RuntimeException(e); }
        return config;
    }

    @BeforeEach
    void setup() {
        uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("TestPlayer");
        settings = TabSettings.from(load("""
            player-format:
              default: "<white><player_name>"
              groups:
                vip: "<green>[VIP] <player_name>"
            afk-format: "<gray>[AFK] <player_name>"
            """));
        manager = new TabManager(settings, afkService, groupResolver, papiHook, plugin);
    }

    @Test
    void resolveDisplayName_usesAfkFormatWhenAfk() {
        when(afkService.isAfk(uuid)).thenReturn(true);
        String result = manager.resolveDisplayName(player, "vip");
        assertThat(result).isEqualTo("<gray>[AFK] <player_name>");
    }

    @Test
    void resolveDisplayName_usesGroupFormatWhenNotAfk() {
        when(afkService.isAfk(uuid)).thenReturn(false);
        String result = manager.resolveDisplayName(player, "vip");
        assertThat(result).isEqualTo("<green>[VIP] <player_name>");
    }

    @Test
    void resolveDisplayName_usesDefaultWhenGroupUnknown() {
        when(afkService.isAfk(uuid)).thenReturn(false);
        String result = manager.resolveDisplayName(player, "unknown");
        assertThat(result).isEqualTo("<white><player_name>");
    }
}
