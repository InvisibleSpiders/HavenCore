package dev.invisiblespiders.haven.core.service;

import com.zaxxer.hikari.HikariConfig;
import dev.invisiblespiders.haven.api.service.DataSourceHealth;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static HikariConfig sqliteConfig() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite::memory:");
        config.setMaximumPoolSize(1);
        config.setPoolName("HavenCoreTest-" + System.nanoTime());
        return config;
    }
}
