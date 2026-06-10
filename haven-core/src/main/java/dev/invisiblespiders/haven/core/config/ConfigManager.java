package dev.invisiblespiders.haven.core.config;

import com.zaxxer.hikari.HikariConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class ConfigManager {

    private final Plugin plugin;
    private FileConfiguration main;
    private FileConfiguration database;
    private FileConfiguration messages;
    private FileConfiguration economy;
    private FileConfiguration storage;
    private FileConfiguration codex;
    private FileConfiguration hooks;
    private FileConfiguration opToggle;

    private static final List<String> CONFIG_FILES = Arrays.asList(
        "config.yml", "database.yml", "messages.yml",
        "economy.yml", "storage.yml", "codex.yml", "hooks.yml", "op-toggle.yml"
    );

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        CONFIG_FILES.forEach(name -> {
            File f = new File(plugin.getDataFolder(), name);
            if (!f.exists()) plugin.saveResource(name, false);
        });
        reload();
    }

    public void reload() {
        main     = load("config.yml");
        database = load("database.yml");
        messages = load("messages.yml");
        economy  = load("economy.yml");
        storage  = load("storage.yml");
        codex    = load("codex.yml");
        hooks    = load("hooks.yml");
        opToggle = load("op-toggle.yml");
        boolean luckPermsInstalled = plugin.getServer().getPluginManager().getPlugin("LuckPerms") != null;
        ConfigDiagnostics.logWarnings(this, luckPermsInstalled, plugin.getLogger());
    }

    public void reloadOpToggle() {
        opToggle = load("op-toggle.yml");
    }

    public void reloadMessages() {
        messages = load("messages.yml");
    }

    private FileConfiguration load(String name) {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), name));
    }

    public FileConfiguration getMain()     { return main; }
    public FileConfiguration getDatabase() { return database; }
    public FileConfiguration getMessages() { return messages; }
    public FileConfiguration getEconomy()  { return economy; }
    public FileConfiguration getStorage()  { return storage; }
    public FileConfiguration getCodex()    { return codex; }
    public FileConfiguration getHooks()    { return hooks; }
    public FileConfiguration getOpToggle() { return opToggle; }

    /** Builds a HikariConfig from database.yml settings. */
    public HikariConfig buildHikariConfig() {
        HikariConfig hc = new HikariConfig();
        String type = database.getString("type", "sqlite");

        if ("mysql".equalsIgnoreCase(type)) {
            String host = database.getString("mysql.host", "localhost");
            int    port = database.getInt("mysql.port", 3306);
            String db   = database.getString("mysql.database", "haven");
            String user = database.getString("mysql.username", "haven");
            String pass = database.getString("mysql.password", "");
            hc.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useSSL=false&autoReconnect=true&characterEncoding=utf8");
            hc.setUsername(user);
            hc.setPassword(pass);
            hc.setMaximumPoolSize(database.getInt("mysql.pool.maximum-pool-size", 10));
            hc.setMinimumIdle(database.getInt("mysql.pool.minimum-idle", 2));
            hc.setConnectionTimeout(database.getLong("mysql.pool.connection-timeout", 30000));
            hc.setIdleTimeout(database.getLong("mysql.pool.idle-timeout", 600000));
            hc.setMaxLifetime(database.getLong("mysql.pool.max-lifetime", 1800000));
        } else {
            String dbFile = database.getString("sqlite.file", "haven.db");
            File f = new File(plugin.getDataFolder(), dbFile);
            hc.setJdbcUrl("jdbc:sqlite:" + f.getAbsolutePath());
            hc.setMaximumPoolSize(1); // SQLite is single-writer
            hc.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON;");
        }

        hc.setPoolName("HavenCore");
        return hc;
    }

    /** Returns the message string for a key, or empty string if missing/blank. */
    public String getMessage(String key) {
        return messages.getString(key, "");
    }

    /** Returns whether messages.yml explicitly defines a key, even when the value is blank. */
    public boolean hasMessage(String key) {
        return messages != null && messages.contains(key);
    }
}
