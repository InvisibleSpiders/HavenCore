package dev.invisiblespiders.haven.core.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

public final class ConfigDiagnostics {

    private static final Set<String> DATABASE_TYPES = Set.of("sqlite", "mysql");

    private ConfigDiagnostics() {}

    public static void logWarnings(ConfigManager config, Logger logger) {
        logWarnings(
            config.getDatabase(),
            config.getEconomy(),
            config.getStorage(),
            config.getHooks(),
            logger
        );
    }

    static void logWarnings(FileConfiguration database, FileConfiguration economy,
                            FileConfiguration storage, FileConfiguration hooks, Logger logger) {
        logDatabaseWarnings(database, logger);
        logEconomyWarnings(economy, logger);
        logStorageWarnings(storage, logger);
        logHookWarnings(hooks, logger);
    }

    private static void logDatabaseWarnings(FileConfiguration database, Logger logger) {
        String type = database.getString("type", "sqlite").toLowerCase(Locale.ROOT);
        if (!DATABASE_TYPES.contains(type)) {
            logger.warning("database.yml type '" + type + "' is invalid; using sqlite. Expected: sqlite, mysql.");
        }
    }

    private static void logEconomyWarnings(FileConfiguration economy, Logger logger) {
        String preferredAdapter = economy.getString("preferred-adapter", "money");
        if (!EconomySettings.Adapter.isValid(preferredAdapter)) {
            logger.warning("economy.yml preferred-adapter '" + preferredAdapter
                + "' is invalid; using money. Expected: money, item.");
        }

        if (!economy.getBoolean("item-currency.enabled", false)) {
            return;
        }

        String material = economy.getString("item-currency.material", "EMERALD");
        if (Material.matchMaterial(material) == null) {
            logger.warning("economy.yml item-currency.material '" + material
                + "' is invalid; using EMERALD.");
        }
    }

    private static void logStorageWarnings(FileConfiguration storage, Logger logger) {
        int rows = storage.getInt("defaults.rows", 3);
        if (rows < 1 || rows > 6) {
            logger.warning("storage.yml defaults.rows must be between 1 and 6; current value is " + rows + ".");
        }
    }

    private static void logHookWarnings(FileConfiguration hooks, Logger logger) {
        if (hooks.getBoolean("hooks.luckperms.enabled", false)) {
            logger.warning("hooks.luckperms.enabled is true, but HavenCore does not currently register a LuckPerms hook.");
        }
    }
}
