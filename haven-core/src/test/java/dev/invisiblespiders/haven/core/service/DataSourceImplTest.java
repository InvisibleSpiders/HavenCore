package dev.invisiblespiders.haven.core.service;

import com.zaxxer.hikari.HikariConfig;
import dev.invisiblespiders.haven.api.service.DataSourceHealth;
import dev.invisiblespiders.haven.api.service.MigrationStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSourceImplTest {

    @Test
    void healthReportsUninitializedPool() {
        DataSourceImpl dataSource = new DataSourceImpl(Logger.getLogger(getClass().getName()));

        DataSourceHealth health = dataSource.health();

        assertFalse(health.initialized());
        assertFalse(health.open());
    }

    @Test
    void healthReportsInitializedPool() {
        DataSourceImpl dataSource = new DataSourceImpl(Logger.getLogger(getClass().getName()));
        dataSource.init(sqliteConfig());
        try {
            DataSourceHealth health = dataSource.health();

            assertTrue(health.initialized());
            assertTrue(health.open());
            assertTrue(health.totalConnections() >= 0);
        } finally {
            dataSource.close();
        }
    }

    @Test
    void healthReportsClosedPool() {
        DataSourceImpl dataSource = new DataSourceImpl(Logger.getLogger(getClass().getName()));
        dataSource.init(sqliteConfig());
        dataSource.close();

        DataSourceHealth health = dataSource.health();

        assertTrue(health.initialized());
        assertFalse(health.open());
    }

    @Test
    void migrationStatusReturnsAppliedVersionsForPlugin() {
        DataSourceImpl dataSource = new DataSourceImpl(Logger.getLogger(getClass().getName()));
        dataSource.init(sqliteConfig());
        try {
            List<MigrationStatus> statuses = dataSource.migrationStatus("haven");

            assertEquals(4, statuses.size());
            assertEquals(1, statuses.get(0).version());
            assertEquals("V1__haven_players.sql", statuses.get(0).script());
            assertEquals(4, statuses.get(3).version());
        } finally {
            dataSource.close();
        }
    }

    @Test
    void migrationStatusIsEmptyForUnknownPlugin() {
        DataSourceImpl dataSource = new DataSourceImpl(Logger.getLogger(getClass().getName()));
        dataSource.init(sqliteConfig());
        try {
            assertTrue(dataSource.migrationStatus("missing").isEmpty());
        } finally {
            dataSource.close();
        }
    }

    @Test
    void registerMigrationsRejectsBlankPluginId() {
        DataSourceImpl dataSource = new DataSourceImpl(Logger.getLogger(getClass().getName()));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> dataSource.registerMigrations(" ", "db/migrations/haven", getClass().getClassLoader()));

        assertTrue(error.getMessage().contains("pluginId"));
    }

    @Test
    void registerMigrationsRejectsBlankLocation() {
        DataSourceImpl dataSource = new DataSourceImpl(Logger.getLogger(getClass().getName()));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> dataSource.registerMigrations("havenclaims", "", getClass().getClassLoader()));

        assertTrue(error.getMessage().contains("location"));
    }

    @Test
    void registerMigrationsRejectsNullLoader() {
        DataSourceImpl dataSource = new DataSourceImpl(Logger.getLogger(getClass().getName()));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> dataSource.registerMigrations("havenclaims", "db/migrations/havenclaims", null));

        assertTrue(error.getMessage().contains("loader"));
    }

    @Test
    void registerMigrationsRequiresInitializedPool() {
        DataSourceImpl dataSource = new DataSourceImpl(Logger.getLogger(getClass().getName()));

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> dataSource.registerMigrations("havenclaims", "db/migrations/havenclaims", getClass().getClassLoader()));

        assertTrue(error.getMessage().contains("not initialized"));
    }

    @Test
    void migrationStatusRejectsBlankPluginId() {
        DataSourceImpl dataSource = new DataSourceImpl(Logger.getLogger(getClass().getName()));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> dataSource.migrationStatus("\t"));

        assertTrue(error.getMessage().contains("pluginId"));
    }

    private static HikariConfig sqliteConfig() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite::memory:");
        config.setMaximumPoolSize(1);
        config.setPoolName("HavenCoreTest-" + System.nanoTime());
        return config;
    }
}
