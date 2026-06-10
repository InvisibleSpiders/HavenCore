package dev.invisiblespiders.haven.api.service;

import javax.sql.DataSource;

public interface HavenDataSource {

    /**
     * Returns the shared HikariCP DataSource.
     * Callers must return connections promptly.
     */
    DataSource getDataSource();

    /**
     * Returns a lightweight snapshot of the shared pool state for diagnostics.
     */
    DataSourceHealth health();

    /**
     * Registers and immediately runs forward-only SQL migrations for a plugin.
     * Call from your plugin's onEnable.
     *
     * <p>The base path must contain a {@code migrations.index} file listing migration
     * filenames (one per line), each named {@code V<number>__<name>.sql}. Applied
     * versions are tracked per plugin in the shared {@code haven_schema_history} table.
     *
     * @param pluginId  short stable ID, e.g. "havenclaims" — namespaces applied versions
     * @param location  classpath base path of the migrations, e.g. "db/migrations/havenclaims"
     * @param loader    ClassLoader that can find the migration resources (your plugin's classloader)
     */
    void registerMigrations(String pluginId, String location, ClassLoader loader);
}
