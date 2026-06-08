package dev.invisiblespiders.haven.core.config;

import org.bukkit.configuration.file.FileConfiguration;

public record HookSettings(
    boolean vaultUnlockedEnabled,
    boolean placeholderApiEnabled,
    boolean luckPermsEnabled
) {

    public static HookSettings from(FileConfiguration config) {
        return new HookSettings(
            config.getBoolean("hooks.vaultunlocked.enabled", true),
            config.getBoolean("hooks.placeholderapi.enabled", true),
            config.getBoolean("hooks.luckperms.enabled", false)
        );
    }
}
