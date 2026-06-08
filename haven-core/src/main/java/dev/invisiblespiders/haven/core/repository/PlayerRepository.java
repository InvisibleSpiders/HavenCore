package dev.invisiblespiders.haven.core.repository;

import dev.invisiblespiders.haven.api.model.HavenPlayer;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

public class PlayerRepository {

    private final DataSource ds;

    public PlayerRepository(DataSource ds) {
        this.ds = ds;
    }

    public Optional<HavenPlayer> findByUuid(UUID uuid) throws SQLException {
        String sql = "SELECT uuid, name_cache, first_seen, last_seen FROM haven_players WHERE uuid = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                HavenPlayer player = new HavenPlayer(
                    UUID.fromString(rs.getString("uuid")),
                    rs.getString("name_cache"),
                    rs.getLong("first_seen"),
                    rs.getLong("last_seen")
                );
                loadPlayerData(c, player);
                return Optional.of(player);
            }
        }
    }

    public Optional<HavenPlayer> findByName(String name) throws SQLException {
        String sql = "SELECT uuid, name_cache, first_seen, last_seen FROM haven_players WHERE LOWER(name_cache) = LOWER(?)";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                HavenPlayer player = new HavenPlayer(
                    UUID.fromString(rs.getString("uuid")),
                    rs.getString("name_cache"),
                    rs.getLong("first_seen"),
                    rs.getLong("last_seen")
                );
                loadPlayerData(c, player);
                return Optional.of(player);
            }
        }
    }

    public void upsert(HavenPlayer player) throws SQLException {
        String sql = """
            INSERT INTO haven_players (uuid, name_cache, first_seen, last_seen)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                name_cache = excluded.name_cache,
                last_seen  = excluded.last_seen
            """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.getUuid().toString());
            ps.setString(2, player.getNameCache());
            ps.setLong(3, player.getFirstSeen());
            ps.setLong(4, player.getLastSeen());
            ps.executeUpdate();
        }
    }

    public void savePluginData(HavenPlayer player, String pluginId) throws SQLException {
        String deleteSql = "DELETE FROM haven_player_data WHERE player_uuid = ? AND plugin_id = ?";
        String insertSql = "INSERT INTO haven_player_data (player_uuid, plugin_id, data_key, value) VALUES (?, ?, ?, ?)";
        Map<String, String> data = player.getDataSnapshot(pluginId);

        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement del = c.prepareStatement(deleteSql)) {
                    del.setString(1, player.getUuid().toString());
                    del.setString(2, pluginId);
                    del.executeUpdate();
                }
                if (!data.isEmpty()) {
                    try (PreparedStatement ins = c.prepareStatement(insertSql)) {
                        for (Map.Entry<String, String> e : data.entrySet()) {
                            ins.setString(1, player.getUuid().toString());
                            ins.setString(2, pluginId);
                            ins.setString(3, e.getKey());
                            ins.setString(4, e.getValue());
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private void loadPlayerData(Connection c, HavenPlayer player) throws SQLException {
        String sql = "SELECT plugin_id, data_key, value FROM haven_player_data WHERE player_uuid = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.getUuid().toString());
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Map<String, String>> grouped = new HashMap<>();
                while (rs.next()) {
                    grouped.computeIfAbsent(rs.getString("plugin_id"), k -> new HashMap<>())
                           .put(rs.getString("data_key"), rs.getString("value"));
                }
                grouped.forEach(player::loadData);
            }
        }
    }
}
