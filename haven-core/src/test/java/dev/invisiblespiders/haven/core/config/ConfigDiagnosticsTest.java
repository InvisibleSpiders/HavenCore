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

        List<LogRecord> records = new ArrayList<>();
        ConfigDiagnostics.logWarnings(database, economy, storage, hooks, logger(records));

        List<String> messages = records.stream()
            .filter(record -> record.getLevel() == Level.WARNING)
            .map(LogRecord::getMessage)
            .toList();

        assertEquals(5, messages.size());
        assertTrue(messages.stream().anyMatch(message -> message.contains("database.yml type 'postgres'")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("preferred-adapter 'barter'")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("item-currency.material 'NOT_A_MATERIAL'")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("storage.yml defaults.rows")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("hooks.luckperms.enabled")));
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
