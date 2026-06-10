package dev.invisiblespiders.haven.core.repository;

import dev.invisiblespiders.haven.api.model.PlayerCodex;

import javax.sql.DataSource;
import java.sql.*;
import java.util.UUID;

public class CodexRepository {

    private final DataSource ds;

    public CodexRepository(DataSource ds) {
        this.ds = ds;
    }

    public PlayerCodex load(UUID playerUuid) throws SQLException {
        PlayerCodex codex = new PlayerCodex(playerUuid);
        String sql = "SELECT entry_key, discovered_at FROM haven_codex_progress WHERE player_uuid = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    codex.markDiscovered(rs.getString("entry_key"), rs.getLong("discovered_at"));
                }
            }
        }
        return codex;
    }

    public boolean insert(UUID playerUuid, String entryKey, long discoveredAt) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO haven_codex_progress (player_uuid, entry_key, discovered_at)
            VALUES (?, ?, ?)
            """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, entryKey);
            ps.setLong(3, discoveredAt);
            return ps.executeUpdate() > 0;
        }
    }

    public int countByCategory(UUID playerUuid, String categoryId) throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM haven_codex_progress
            WHERE player_uuid = ? AND entry_key LIKE ? ESCAPE '\\'
            """;
        String escaped = categoryId.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, escaped + ":%");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
