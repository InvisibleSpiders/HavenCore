package dev.invisiblespiders.haven.core.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public final class ConfigDiagnostics {

    private static final Set<String> DATABASE_TYPES = Set.of("sqlite", "mysql");

    private ConfigDiagnostics() {}

    public static void logWarnings(ConfigManager config, Logger logger) {
        logWarnings(config, false, logger);
    }

    public static void logWarnings(ConfigManager config, boolean luckPermsInstalled, Logger logger) {
        logWarnings(
            config.getDatabase(),
            config.getEconomy(),
            config.getStorage(),
            config.getHooks(),
            config.getOpToggle(),
            luckPermsInstalled,
            logger
        );
    }

    static void logWarnings(FileConfiguration database, FileConfiguration economy,
                            FileConfiguration storage, FileConfiguration hooks, Logger logger) {
        logWarnings(database, economy, storage, hooks, null, false, logger);
    }

    static void logWarnings(FileConfiguration database, FileConfiguration economy,
                            FileConfiguration storage, FileConfiguration hooks,
                            FileConfiguration opToggle, Logger logger) {
        logWarnings(database, economy, storage, hooks, opToggle, false, logger);
    }

    static void logWarnings(FileConfiguration database, FileConfiguration economy,
                            FileConfiguration storage, FileConfiguration hooks,
                            boolean luckPermsInstalled, Logger logger) {
        logWarnings(database, economy, storage, hooks, null, luckPermsInstalled, logger);
    }

    static void logWarnings(FileConfiguration database, FileConfiguration economy,
                            FileConfiguration storage, FileConfiguration hooks,
                            FileConfiguration opToggle,
                            boolean luckPermsInstalled, Logger logger) {
        logDatabaseWarnings(database, logger);
        logEconomyWarnings(economy, logger);
        logStorageWarnings(storage, logger);
        logHookWarnings(hooks, luckPermsInstalled, logger);
        if (opToggle != null) {
            logOpToggleWarnings(opToggle, logger);
        }
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

    private static void logHookWarnings(FileConfiguration hooks, boolean luckPermsInstalled, Logger logger) {
        HookSettings hookSettings = HookSettings.from(hooks);
        if (hookSettings.luckPermsEnabled() && !luckPermsInstalled) {
            logger.warning("hooks.luckperms.enabled is true, but LuckPerms is not installed.");
        }
    }

    private static void logOpToggleWarnings(FileConfiguration opToggle, Logger logger) {
        ConfigurationSection players = opToggle.getConfigurationSection("players");
        int validEntries = 0;
        Map<String, String> seenCodes = new HashMap<>();
        if (players != null) {
            for (String name : players.getKeys(false)) {
                String uuidValue = players.getString(name + ".uuid");
                boolean validUuid = isValidUuid(uuidValue);
                if (!validUuid) {
                    logger.warning("op-toggle.yml players." + name + ".uuid is invalid; expected a UUID.");
                }

                String code = players.getString(name + ".code", "");
                boolean validCode = OpToggleSettings.isValidCode(code);
                if (!validCode) {
                    logger.warning("op-toggle.yml players." + name
                        + ".code is invalid; expected exactly 5 alphanumeric characters.");
                }

                if (validUuid && validCode) {
                    validEntries++;
                    String normalizedCode = code.toLowerCase(Locale.ROOT);
                    String previousName = seenCodes.putIfAbsent(normalizedCode, name);
                    if (previousName != null) {
                        logger.warning("op-toggle.yml code '" + code
                            + "' is used by both " + previousName + " and " + name + ".");
                    }
                }
            }
        }

        if (opToggle.getBoolean("enabled", false) && validEntries == 0) {
            logger.warning("op-toggle.yml is enabled but has no valid players; /haven toggleop cannot be used.");
        }
    }

    private static boolean isValidUuid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
