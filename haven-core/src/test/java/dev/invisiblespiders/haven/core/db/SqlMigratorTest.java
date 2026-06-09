package dev.invisiblespiders.haven.core.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlMigratorTest {

    @TempDir
    Path tempDir;

    @Test
    void duplicateMigrationVersionsFailBeforeSqlRuns() throws Exception {
        SQLiteDataSource dataSource = dataSource("duplicate.db");

        IOException error = assertThrows(IOException.class, () -> SqlMigrator.migrate(
            dataSource,
            "duplicate-test",
            "db/migrations/duplicate-version",
            getClass().getClassLoader()
        ));

        assertTrue(error.getMessage().contains("Duplicate migration version 1"));
        assertFalse(tableExists(dataSource, "duplicate_marker"));
        assertFalse(tableExists(dataSource, "duplicate_marker_second"));
    }

    @Test
    void outOfOrderMigrationVersionsFailBeforeSqlRuns() throws Exception {
        SQLiteDataSource dataSource = dataSource("out-of-order.db");

        IOException error = assertThrows(IOException.class, () -> SqlMigrator.migrate(
            dataSource,
            "order-test",
            "db/migrations/out-of-order",
            getClass().getClassLoader()
        ));

        assertTrue(error.getMessage().contains("out of order"));
        assertFalse(tableExists(dataSource, "order_marker_first"));
        assertFalse(tableExists(dataSource, "order_marker_second"));
    }

    @Test
    void renamedAppliedMigrationFailsInsteadOfSkippingVersion() throws Exception {
        SQLiteDataSource dataSource = dataSource("renamed.db");
        recordAppliedMigration(dataSource, "rename-test", 1, "V1__original.sql");

        SQLException error = assertThrows(SQLException.class, () -> SqlMigrator.migrate(
            dataSource,
            "rename-test",
            "db/migrations/renamed-applied",
            getClass().getClassLoader()
        ));

        assertTrue(error.getMessage().contains("recorded as V1__original.sql"));
        assertTrue(error.getMessage().contains("V1__renamed.sql"));
        assertFalse(tableExists(dataSource, "renamed_marker"));
    }

    private SQLiteDataSource dataSource(String fileName) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve(fileName).toAbsolutePath());
        return dataSource;
    }

    private static boolean tableExists(SQLiteDataSource dataSource, String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(null, null, tableName, null)) {
            return tables.next();
        }
    }

    private static void recordAppliedMigration(SQLiteDataSource dataSource, String pluginId, int version, String script)
            throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("CREATE TABLE haven_schema_history ("
                + "plugin_id TEXT NOT NULL, "
                + "version INTEGER NOT NULL, "
                + "script TEXT NOT NULL, "
                + "applied_at INTEGER NOT NULL, "
                + "PRIMARY KEY (plugin_id, version))");
            try (var statement = connection.prepareStatement(
                "INSERT INTO haven_schema_history (plugin_id, version, script, applied_at) VALUES (?, ?, ?, ?)")) {
                statement.setString(1, pluginId);
                statement.setInt(2, version);
                statement.setString(3, script);
                statement.setLong(4, 1L);
                statement.executeUpdate();
            }
        }
    }
}
