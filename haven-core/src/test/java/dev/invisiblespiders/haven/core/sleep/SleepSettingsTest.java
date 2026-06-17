package dev.invisiblespiders.haven.core.sleep;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SleepSettingsTest {

    @Test
    void defaultsFromEmptyConfig() {
        SleepSettings s = SleepSettings.from(new YamlConfiguration());
        assertThat(s.worlds()).containsExactly("world");
        assertThat(s.minCount()).isEqualTo(1);
        assertThat(s.minPercent()).isEqualTo(0);
        assertThat(s.minSpeed()).isEqualTo(40L);
        assertThat(s.maxSpeed()).isEqualTo(200L);
    }

    @Test
    void readsConfiguredValues() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("worlds", List.of("world", "world_the_end"));
        config.set("threshold.min-count", 2);
        config.set("threshold.min-percent", 50);
        config.set("speed.min", 20);
        config.set("speed.max", 100);

        SleepSettings s = SleepSettings.from(config);

        assertThat(s.worlds()).containsExactly("world", "world_the_end");
        assertThat(s.minCount()).isEqualTo(2);
        assertThat(s.minPercent()).isEqualTo(50);
        assertThat(s.minSpeed()).isEqualTo(20L);
        assertThat(s.maxSpeed()).isEqualTo(100L);
    }

    @Test
    void worldsListIsImmutable() {
        SleepSettings s = SleepSettings.from(new YamlConfiguration());
        assertThrows(UnsupportedOperationException.class, () -> s.worlds().add("extra"));
    }

    @Test
    void messagesHaveNonBlankDefaults() {
        SleepSettings s = SleepSettings.from(new YamlConfiguration());
        assertThat(s.messages().sleeping()).isNotBlank();
        assertThat(s.messages().skipStart()).isNotBlank();
        assertThat(s.messages().skipPaused()).isNotBlank();
        assertThat(s.messages().skipComplete()).isNotBlank();
        assertThat(s.messages().broadcastSkip()).isNotBlank();
    }
}
