package dev.invisiblespiders.haven.core.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.invisiblespiders.haven.api.service.HavenDataSource;
import org.flywaydb.core.Flyway;

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
        // Run HavenAPI's own migrations immediately
        runFlyway("haven", "classpath:db/migrations/haven", getClass().getClassLoader());
        logger.info("Database pool initialized.");
    }

    @Override
    public DataSource getDataSource() {
        return pool;
    }

    @Override
    public void registerMigrations(String pluginId, String location, ClassLoader loader) {
        runFlyway(pluginId, location, loader);
    }

    private void runFlyway(String pluginId, String location, ClassLoader loader) {
        try {
            Flyway.configure()
                .classLoader(loader)
                .dataSource(pool)
                .locations(location)
                .table("flyway_schema_history_" + pluginId)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
            logger.fine("Flyway migrations complete for: " + pluginId);
        } catch (Exception e) {
            logger.severe("Flyway migration failed for plugin '" + pluginId + "': " + e.getMessage());
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
