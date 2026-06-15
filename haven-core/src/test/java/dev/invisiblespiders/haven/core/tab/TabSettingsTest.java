package dev.invisiblespiders.haven.core.tab;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TabSettingsTest {

    private static YamlConfiguration load(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try { config.loadFromString(yaml); }
        catch (InvalidConfigurationException e) { throw new RuntimeException(e); }
        return config;
    }

    @Test
    void parsesDefaultValues() {
        TabSettings settings = TabSettings.from(load(""));
        assertThat(settings.refreshInterval()).isEqualTo(5);
        assertThat(settings.header()).isEmpty();
        assertThat(settings.footer()).isEmpty();
        assertThat(settings.playerFormat().defaultFormat()).isNotBlank();
        assertThat(settings.afkFormat()).isNotBlank();
    }

    @Test
    void parsesHeaderAndFooter() {
        TabSettings settings = TabSettings.from(load("""
            header:
              - "<gold>Line 1"
              - "<gray>Line 2"
            footer:
              - "<gray>Online: %server_online%"
            """));
        assertThat(settings.header()).containsExactly("<gold>Line 1", "<gray>Line 2");
        assertThat(settings.footer()).containsExactly("<gray>Online: %server_online%");
    }

    @Test
    void groupFormatFallsBackToDefault() {
        TabSettings settings = TabSettings.from(load("""
            player-format:
              default: "<white><player_name>"
              groups:
                vip: "<green>[VIP] <player_name>"
            """));
        assertThat(settings.playerFormat().resolveFormat("unknown_group")).isEqualTo("<white><player_name>");
        assertThat(settings.playerFormat().resolveFormat("vip")).isEqualTo("<green>[VIP] <player_name>");
    }
}
