package dev.invisiblespiders.haven.core.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpToggleSettingsTest {

    @Test
    void readsEnabledUuidBoundToggleEntries() {
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        YamlConfiguration config = new YamlConfiguration();
        config.set("enabled", true);
        config.set("players.InvisibleSpiders.uuid", uuid.toString());
        config.set("players.InvisibleSpiders.code", "A5B27");

        OpToggleSettings settings = OpToggleSettings.from(config);

        assertTrue(settings.enabled());
        OpToggleSettings.Entry entry = settings.find(uuid).orElseThrow();
        assertEquals("InvisibleSpiders", entry.name());
        assertEquals(uuid, entry.uuid());
        assertEquals("A5B27", entry.code());
        assertEquals("havencore.toggleop.a5b27", entry.permission());
    }

    @Test
    void ignoresEntriesWithoutValidUuidOrFiveCharacterCode() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("enabled", true);
        config.set("players.BadUuid.uuid", "not-a-uuid");
        config.set("players.BadUuid.code", "A5B27");
        config.set("players.BadCode.uuid", "00000000-0000-0000-0000-000000000002");
        config.set("players.BadCode.code", "LONGER");

        OpToggleSettings settings = OpToggleSettings.from(config);

        assertFalse(settings.find(UUID.fromString("00000000-0000-0000-0000-000000000002")).isPresent());
        assertTrue(settings.entries().isEmpty());
    }
}
