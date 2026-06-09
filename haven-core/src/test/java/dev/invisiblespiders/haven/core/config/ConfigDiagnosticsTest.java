package dev.invisiblespiders.haven.core.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigDiagnosticsTest {

    @Test
    void logsWarningsForInvalidStartupConfiguration() {
        YamlConfiguration database = new YamlConfiguration();
        database.set("type", "postgres");

        YamlConfiguration economy = new YamlConfiguration();
        economy.set("preferred-adapter", "barter");
        economy.set("item-currency.enabled", true);
        economy.set("item-currency.material", "NOT_A_MATERIAL");

        YamlConfiguration storage = new YamlConfiguration();
        storage.set("defaults.rows", 9);

        YamlConfiguration hooks = new YamlConfiguration();
        hooks.set("hooks.luckperms.enabled", true);

        YamlConfiguration opToggle = new YamlConfiguration();
        opToggle.set("enabled", true);
        opToggle.set("players.BadUuid.uuid", "not-a-uuid");
        opToggle.set("players.BadUuid.code", "A5B27");
        opToggle.set("players.BadCode.uuid", "00000000-0000-0000-0000-000000000001");
        opToggle.set("players.BadCode.code", "LONGER");
        opToggle.set("players.DuplicateOne.uuid", "00000000-0000-0000-0000-000000000002");
        opToggle.set("players.DuplicateOne.code", "B7C91");
        opToggle.set("players.DuplicateTwo.uuid", "00000000-0000-0000-0000-000000000003");
        opToggle.set("players.DuplicateTwo.code", "B7C91");
        opToggle.set("players.DuplicateUuidOne.uuid", "00000000-0000-0000-0000-000000000004");
        opToggle.set("players.DuplicateUuidOne.code", "C8D02");
        opToggle.set("players.DuplicateUuidTwo.uuid", "00000000-0000-0000-0000-000000000004");
        opToggle.set("players.DuplicateUuidTwo.code", "D9E13");

        List<LogRecord> records = new ArrayList<>();
        ConfigDiagnostics.logWarnings(database, economy, storage, hooks, opToggle, logger(records));

        List<String> messages = records.stream()
            .filter(record -> record.getLevel() == Level.WARNING)
            .map(LogRecord::getMessage)
            .toList();

        assertEquals(9, messages.size());
        assertTrue(messages.stream().anyMatch(message -> message.contains("database.yml type 'postgres'")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("preferred-adapter 'barter'")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("item-currency.material 'NOT_A_MATERIAL'")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("storage.yml defaults.rows")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("hooks.luckperms.enabled")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("op-toggle.yml players.BadUuid.uuid")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("op-toggle.yml players.BadCode.code")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("op-toggle.yml code 'B7C91'")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("op-toggle.yml UUID '00000000-0000-0000-0000-000000000004'")));
    }

    @Test
    void warnsWhenOpToggleIsEnabledWithoutValidPlayers() {
        YamlConfiguration database = new YamlConfiguration();
        database.set("type", "sqlite");
        YamlConfiguration economy = new YamlConfiguration();
        YamlConfiguration storage = new YamlConfiguration();
        storage.set("defaults.rows", 3);
        YamlConfiguration hooks = new YamlConfiguration();
        hooks.set("hooks.luckperms.enabled", false);
        YamlConfiguration opToggle = new YamlConfiguration();
        opToggle.set("enabled", true);

        List<LogRecord> records = new ArrayList<>();
        ConfigDiagnostics.logWarnings(database, economy, storage, hooks, opToggle, logger(records));

        List<String> messages = records.stream()
            .filter(record -> record.getLevel() == Level.WARNING)
            .map(LogRecord::getMessage)
            .toList();

        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("op-toggle.yml is enabled but has no valid players"));
    }

    @Test
    void acceptsRootOpTogglePlayerEntry() {
        YamlConfiguration database = new YamlConfiguration();
        database.set("type", "sqlite");
        YamlConfiguration economy = new YamlConfiguration();
        YamlConfiguration storage = new YamlConfiguration();
        storage.set("defaults.rows", 3);
        YamlConfiguration hooks = new YamlConfiguration();
        hooks.set("hooks.luckperms.enabled", false);
        YamlConfiguration opToggle = new YamlConfiguration();
        opToggle.set("enabled", true);
        opToggle.set("player", "00000000-0000-0000-0000-000000000001");
        opToggle.set("code", "2410a");

        List<LogRecord> records = new ArrayList<>();
        ConfigDiagnostics.logWarnings(database, economy, storage, hooks, opToggle, logger(records));

        assertTrue(records.isEmpty());
    }

    @Test
    void acceptsDefaultConfigurationValues() {
        YamlConfiguration database = new YamlConfiguration();
        database.set("type", "sqlite");

        YamlConfiguration economy = new YamlConfiguration();
        economy.set("item-currency.enabled", true);
        economy.set("item-currency.material", "EMERALD");

        YamlConfiguration storage = new YamlConfiguration();
        storage.set("defaults.rows", 3);

        YamlConfiguration hooks = new YamlConfiguration();
        hooks.set("hooks.luckperms.enabled", false);

        List<LogRecord> records = new ArrayList<>();
        ConfigDiagnostics.logWarnings(database, economy, storage, hooks, logger(records));

        assertTrue(records.isEmpty());
    }

    @Test
    void acceptsEnabledLuckPermsWhenPluginIsInstalled() {
        YamlConfiguration database = new YamlConfiguration();
        database.set("type", "sqlite");

        YamlConfiguration economy = new YamlConfiguration();

        YamlConfiguration storage = new YamlConfiguration();
        storage.set("defaults.rows", 3);

        YamlConfiguration hooks = new YamlConfiguration();
        hooks.set("hooks.luckperms.enabled", true);

        List<LogRecord> records = new ArrayList<>();
        ConfigDiagnostics.logWarnings(database, economy, storage, hooks, true, logger(records));

        assertTrue(records.isEmpty());
    }

    private static Logger logger(List<LogRecord> records) {
        Logger logger = Logger.getLogger("ConfigDiagnosticsTest-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        return logger;
    }
}
