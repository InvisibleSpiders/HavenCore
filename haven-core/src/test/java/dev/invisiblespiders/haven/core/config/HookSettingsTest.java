package dev.invisiblespiders.haven.core.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HookSettingsTest {

    @Test
    void defaultsCoreHooksOnAndUnimplementedLuckPermsOff() {
        HookSettings settings = HookSettings.from(new YamlConfiguration());

        assertTrue(settings.vaultUnlockedEnabled());
        assertTrue(settings.placeholderApiEnabled());
        assertFalse(settings.luckPermsEnabled());
    }

    @Test
    void readsConfiguredHookSettings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("hooks.vaultunlocked.enabled", false);
        config.set("hooks.placeholderapi.enabled", false);
        config.set("hooks.luckperms.enabled", true);

        HookSettings settings = HookSettings.from(config);

        assertFalse(settings.vaultUnlockedEnabled());
        assertFalse(settings.placeholderApiEnabled());
        assertTrue(settings.luckPermsEnabled());
    }
}
