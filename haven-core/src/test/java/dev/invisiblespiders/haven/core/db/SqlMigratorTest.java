package dev.invisiblespiders.haven.core.db;

import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlMigratorTest {

    @Test
    void duplicateMigrationVersionsFailBeforeSqlRuns() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite::memory:");

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

    private static boolean tableExists(SQLiteDataSource dataSource, String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(null, null, tableName, null)) {
            return tables.next();
        }
    }
}
