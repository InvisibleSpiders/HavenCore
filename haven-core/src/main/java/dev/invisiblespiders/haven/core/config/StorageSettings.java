package dev.invisiblespiders.haven.core.config;

import org.bukkit.configuration.file.FileConfiguration;

public record StorageSettings(int defaultRows, int maxPerPlayer) {

    public static StorageSettings defaults() {
        return new StorageSettings(3, 0);
    }

    public static StorageSettings from(FileConfiguration config) {
        int defaultRows = config.getInt("defaults.rows", 3);
        int maxPerPlayer = config.getInt("max-per-player", 0);
        return new StorageSettings(normalizeDefaultRows(defaultRows), Math.max(0, maxPerPlayer));
    }

    public static boolean isValidRows(int rows) {
        return rows >= 1 && rows <= 6;
    }

    private static int normalizeDefaultRows(int rows) {
        return isValidRows(rows) ? rows : 3;
    }
}
