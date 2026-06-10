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
    void readsSingleRootPlayerEntry() {
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        YamlConfiguration config = new YamlConfiguration();
        config.set("enabled", true);
        config.set("player", uuid.toString());
        config.set("code", "2410a");

        OpToggleSettings settings = OpToggleSettings.from(config);

        assertTrue(settings.enabled());
        assertEquals(1, settings.entries().size());
        OpToggleSettings.Entry entry = settings.find(uuid).orElseThrow();
        assertEquals("player", entry.name());
        assertEquals(uuid, entry.uuid());
        assertEquals("2410a", entry.code());
        assertEquals("havencore.toggleop.2410a", entry.permission());
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

    @Test
    void ignoresDuplicateUuidEntriesAfterFirstValidEntry() {
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        YamlConfiguration config = new YamlConfiguration();
        config.set("enabled", true);
        config.set("players.First.uuid", uuid.toString());
        config.set("players.First.code", "A5B27");
        config.set("players.Second.uuid", uuid.toString());
        config.set("players.Second.code", "B7C91");

        OpToggleSettings settings = OpToggleSettings.from(config);

        assertEquals(1, settings.entries().size());
        OpToggleSettings.Entry entry = settings.find(uuid).orElseThrow();
        assertEquals("First", entry.name());
        assertEquals("A5B27", entry.code());
    }

    @Test
    void ignoresDuplicateCodeEntriesAfterFirstValidEntry() {
        UUID firstUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        YamlConfiguration config = new YamlConfiguration();
        config.set("enabled", true);
        config.set("players.First.uuid", firstUuid.toString());
        config.set("players.First.code", "A5B27");
        config.set("players.Second.uuid", secondUuid.toString());
        config.set("players.Second.code", "a5b27");

        OpToggleSettings settings = OpToggleSettings.from(config);

        assertEquals(1, settings.entries().size());
        assertTrue(settings.find(firstUuid).isPresent());
        assertFalse(settings.find(secondUuid).isPresent());
    }
}
