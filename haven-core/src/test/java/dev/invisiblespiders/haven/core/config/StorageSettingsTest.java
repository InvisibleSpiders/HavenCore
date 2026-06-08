package dev.invisiblespiders.haven.core.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageSettingsTest {

    @Test
    void readsConfiguredStorageSettings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("defaults.rows", 5);
        config.set("max-per-player", 2);

        StorageSettings settings = StorageSettings.from(config);

        assertEquals(5, settings.defaultRows());
        assertEquals(2, settings.maxPerPlayer());
    }

    @Test
    void normalizesInvalidStorageSettingsToSafeDefaults() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("defaults.rows", 9);
        config.set("max-per-player", -4);

        StorageSettings settings = StorageSettings.from(config);

        assertEquals(3, settings.defaultRows());
        assertEquals(0, settings.maxPerPlayer());
    }
}
