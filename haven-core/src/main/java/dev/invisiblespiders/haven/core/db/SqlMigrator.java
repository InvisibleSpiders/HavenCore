package dev.invisiblespiders.haven.core.db;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal forward-only SQL migrator. No external dependencies.
 *
 * Each plugin ships an ordered index file at {basePath}/migrations.index listing
 * migration filenames, one per line. Filenames follow V{number}__{name}.sql.
 * Applied versions are tracked per plugin in haven_schema_history.
 *
 * Statements within a file are separated by ';'. Comment lines (-- ...) are stripped.
 */
public final class SqlMigrator {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+)__.*\\.sql$");

    private SqlMigrator() {}

    public static void migrate(DataSource ds, String pluginId, String basePath, ClassLoader loader)
            throws SQLException, IOException {

        List<String> files = readIndex(basePath, loader);

        try (Connection c = ds.getConnection()) {
            ensureHistoryTable(c);
            Set<Integer> applied = appliedVersions(c, pluginId);

            for (String file : files) {
                Matcher m = VERSION_PATTERN.matcher(file);
                if (!m.matches()) {
                    throw new IOException("Bad migration filename (expected V<n>__name.sql): " + file);
                }
                int version = Integer.parseInt(m.group(1));
                if (applied.contains(version)) continue;

                String sql = readResource(basePath + "/" + file, loader);
                applyMigration(c, pluginId, version, file, sql);
            }
        }
    }

    private static List<String> readIndex(String basePath, ClassLoader loader) throws IOException {
        String indexPath = basePath + "/migrations.index";
        List<String> files = new ArrayList<>();
        try (InputStream in = loader.getResourceAsStream(indexPath)) {
            if (in == null) throw new IOException("Migration index not found on classpath: " + indexPath);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) files.add(trimmed);
                }
            }
        }
        return files;
    }

    private static String readResource(String path, ClassLoader loader) throws IOException {
        try (InputStream in = loader.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Migration resource not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void ensureHistoryTable(Connection c) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS haven_schema_history ("
            + "plugin_id TEXT NOT NULL, "
            + "version INTEGER NOT NULL, "
            + "script TEXT NOT NULL, "
            + "applied_at INTEGER NOT NULL, "
            + "PRIMARY KEY (plugin_id, version))";
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private static Set<Integer> appliedVersions(Connection c, String pluginId) throws SQLException {
        Set<Integer> versions = new HashSet<>();
        String sql = "SELECT version FROM haven_schema_history WHERE plugin_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, pluginId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) versions.add(rs.getInt(1));
            }
        }
        return versions;
    }

    private static void applyMigration(Connection c, String pluginId, int version, String script, String sql)
            throws SQLException {
        boolean autoCommit = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            try (Statement st = c.createStatement()) {
                for (String stmt : splitStatements(sql)) {
                    st.execute(stmt);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO haven_schema_history (plugin_id, version, script, applied_at) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, pluginId);
                ps.setInt(2, version);
                ps.setString(3, script);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            }
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw new SQLException("Migration failed for " + pluginId + " " + script + ": " + e.getMessage(), e);
        } finally {
            c.setAutoCommit(autoCommit);
        }
    }

    /** Splits on ';' after stripping line comments. Adequate for simple DDL migrations. */
    private static List<String> splitStatements(String sql) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : sql.split("\n")) {
            String noComment = line;
            int idx = noComment.indexOf("--");
            if (idx >= 0) noComment = noComment.substring(0, idx);
            cleaned.append(noComment).append('\n');
        }
        List<String> statements = new ArrayList<>();
        for (String part : cleaned.toString().split(";")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) statements.add(trimmed);
        }
        return statements;
    }
}
