package dev.invisiblespiders.haven.api.service;

import javax.sql.DataSource;

public interface HavenDataSource {

    /**
     * Returns the shared HikariCP DataSource.
     * Callers must return connections promptly.
     */
    DataSource getDataSource();

    /**
     * Registers and immediately runs Flyway migrations for a plugin.
     * Call from your plugin's onEnable.
     *
     * @param pluginId   short stable ID, e.g. "havenclaims" — used as Flyway history table suffix
     * @param location   Flyway location string, e.g. "classpath:db/migrations/havenclaims"
     * @param loader     ClassLoader that can find the migration resources (your plugin's classloader)
     */
    void registerMigrations(String pluginId, String location, ClassLoader loader);
}
