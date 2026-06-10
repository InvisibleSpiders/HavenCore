package dev.invisiblespiders.haven.core.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Logger;

public final class ConfigDiagnostics {

    private static final Set<String> DATABASE_TYPES = Set.of("sqlite", "mysql");
    private static final Set<String> DATABASE_KEYS = Set.of(
        "type",
        "sqlite",
        "sqlite.file",
        "mysql",
        "mysql.host",
        "mysql.port",
        "mysql.database",
        "mysql.username",
        "mysql.password",
        "mysql.pool",
        "mysql.pool.maximum-pool-size",
        "mysql.pool.minimum-idle",
        "mysql.pool.connection-timeout",
        "mysql.pool.idle-timeout",
        "mysql.pool.max-lifetime"
    );
    private static final Set<String> ECONOMY_KEYS = Set.of(
        "preferred-adapter",
        "item-currency",
        "item-currency.enabled",
        "item-currency.material",
        "item-currency.pdc-tag",
        "item-currency.display-name",
        "item-currency.lore"
    );
    private static final Set<String> STORAGE_KEYS = Set.of(
        "defaults",
        "defaults.rows",
        "max-per-player"
    );
    private static final Set<String> HOOK_KEYS = Set.of(
        "hooks",
        "hooks.vaultunlocked",
        "hooks.vaultunlocked.enabled",
        "hooks.placeholderapi",
        "hooks.placeholderapi.enabled",
        "hooks.luckperms",
        "hooks.luckperms.enabled"
    );
    private static final Set<String> OP_TOGGLE_KEYS = Set.of(
        "enabled",
        "player",
        "uuid",
        "code",
        "players"
    );

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
        logUnknownKeyWarnings("database.yml", database, DATABASE_KEYS, key -> false, logger);
        logUnknownKeyWarnings("economy.yml", economy, ECONOMY_KEYS, key -> false, logger);
        logUnknownKeyWarnings("storage.yml", storage, STORAGE_KEYS, key -> false, logger);
        logUnknownKeyWarnings("hooks.yml", hooks, HOOK_KEYS, key -> false, logger);
        if (opToggle != null) {
            logUnknownKeyWarnings("op-toggle.yml", opToggle, OP_TOGGLE_KEYS, ConfigDiagnostics::isOpTogglePlayerKey, logger);
        }
        logDatabaseWarnings(database, logger);
        logEconomyWarnings(economy, logger);
        logStorageWarnings(storage, logger);
        logHookWarnings(hooks, luckPermsInstalled, logger);
        if (opToggle != null) {
            logOpToggleWarnings(opToggle, logger);
        }
    }

    private static void logUnknownKeyWarnings(String fileName, FileConfiguration config,
                                              Set<String> allowedKeys, Predicate<String> dynamicAllowed,
                                              Logger logger) {
        Set<String> reportedUnknownParents = new HashSet<>();
        for (String key : config.getKeys(true)) {
            if (allowedKeys.contains(key) || dynamicAllowed.test(key)) {
                continue;
            }
            if (hasReportedUnknownParent(key, reportedUnknownParents)) {
                continue;
            }
            reportedUnknownParents.add(key);
            logger.warning(fileName + " unknown key '" + key + "'; check spelling or remove it.");
        }
    }

    private static boolean hasReportedUnknownParent(String key, Set<String> reportedUnknownParents) {
        int separator = key.indexOf('.');
        while (separator > 0) {
            if (reportedUnknownParents.contains(key.substring(0, separator))) {
                return true;
            }
            separator = key.indexOf('.', separator + 1);
        }
        return false;
    }

    private static boolean isOpTogglePlayerKey(String key) {
        if (!key.startsWith("players.")) {
            return false;
        }
        String[] parts = key.split("\\.");
        if (parts.length == 2) {
            return true;
        }
        return parts.length == 3 && Set.of("uuid", "code").contains(parts[2]);
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
        int validEntries = countRootOpToggleEntry(opToggle, logger);
        Map<String, String> seenCodes = new HashMap<>();
        Map<UUID, String> seenUuids = new HashMap<>();
        String rootCode = opToggle.getString("code", "");
        if (validEntries > 0) {
            seenCodes.put(OpToggleSettings.normalizeCode(rootCode), "player");
            seenUuids.put(UUID.fromString(opToggle.getString("player", opToggle.getString("uuid"))), "player");
        }
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
                    UUID uuid = UUID.fromString(uuidValue);
                    String previousUuidName = seenUuids.putIfAbsent(uuid, name);
                    if (previousUuidName != null) {
                        logger.warning("op-toggle.yml UUID '" + uuidValue
                            + "' is used by both " + previousUuidName + " and " + name + ".");
                    }

                    String normalizedCode = OpToggleSettings.normalizeCode(code);
                    String previousName = seenCodes.putIfAbsent(normalizedCode, name);
                    if (previousName != null) {
                        logger.warning("op-toggle.yml code '" + code
                            + "' is used by both " + previousName + " and " + name + ".");
                    }

                    if (previousUuidName == null && previousName == null) {
                        validEntries++;
                    }
                }
            }
        }

        if (opToggle.getBoolean("enabled", false) && validEntries == 0) {
            logger.warning("op-toggle.yml is enabled but has no valid players; /haven toggleop cannot be used.");
        }
    }

    private static int countRootOpToggleEntry(FileConfiguration opToggle, Logger logger) {
        String rootPlayer = opToggle.getString("player", opToggle.getString("uuid"));
        String rootCode = opToggle.getString("code", "");
        if ((rootPlayer == null || rootPlayer.isBlank()) && rootCode.isBlank()) {
            return 0;
        }

        boolean validUuid = isValidUuid(rootPlayer);
        if (!validUuid) {
            logger.warning("op-toggle.yml player is invalid; expected a UUID.");
        }

        boolean validCode = OpToggleSettings.isValidCode(rootCode);
        if (!validCode) {
            logger.warning("op-toggle.yml code is invalid; expected exactly 5 alphanumeric characters.");
        }

        return validUuid && validCode ? 1 : 0;
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
