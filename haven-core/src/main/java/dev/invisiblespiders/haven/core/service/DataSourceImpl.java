package dev.invisiblespiders.haven.core.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import dev.invisiblespiders.haven.api.service.DataSourceHealth;
import dev.invisiblespiders.haven.api.service.HavenDataSource;
import dev.invisiblespiders.haven.core.db.SqlMigrator;

import javax.sql.DataSource;
import java.util.logging.Logger;

public class DataSourceImpl implements HavenDataSource {

    private final Logger logger;
    private HikariDataSource pool;

    public DataSourceImpl(Logger logger) {
        this.logger = logger;
    }

    public void init(HikariConfig config) {
        pool = new HikariDataSource(config);
        // Run HavenCore's own migrations immediately
        registerMigrations("haven", "db/migrations/haven", getClass().getClassLoader());
        logger.info("Database pool initialized.");
    }

    @Override
    public DataSource getDataSource() {
        return pool;
    }

    @Override
    public DataSourceHealth health() {
        if (pool == null) {
            return DataSourceHealth.uninitialized();
        }
        if (pool.isClosed()) {
            return DataSourceHealth.closed();
        }
        HikariPoolMXBean bean = pool.getHikariPoolMXBean();
        if (bean == null) {
            return new DataSourceHealth(true, true, 0, 0, 0, 0);
        }
        return new DataSourceHealth(
            true,
            true,
            bean.getActiveConnections(),
            bean.getIdleConnections(),
            bean.getTotalConnections(),
            bean.getThreadsAwaitingConnection()
        );
    }

    @Override
    public void registerMigrations(String pluginId, String location, ClassLoader loader) {
        try {
            SqlMigrator.migrate(pool, pluginId, location, loader);
            logger.fine("Migrations complete for: " + pluginId);
        } catch (Exception e) {
            logger.severe("Migration failed for plugin '" + pluginId + "': " + e.getMessage());
            throw new RuntimeException("Migration failure for " + pluginId, e);
        }
    }

    public void close() {
        if (pool != null && !pool.isClosed()) {
            pool.close();
            logger.info("Database pool closed.");
        }
    }
}
