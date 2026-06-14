package dev.invisiblespiders.haven.core.upgrade;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class UpgradeRepository {

    private final DataSource dataSource;

    public UpgradeRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public int currentLevel(UUID playerId, String upgradeId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(upgradeId, "upgradeId");

        String sql = """
                SELECT current_level
                FROM haven_upgrade_levels
                WHERE player_uuid = ? AND upgrade_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, upgradeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return 0;
                }
                return resultSet.getInt("current_level");
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to read current upgrade level for " + playerId + " and " + upgradeId, e);
        }
    }

    public void recordPurchase(String providerId, String upgradeId, UUID beneficiaryId,
                               String targetScope, int level, UUID purchaserId, String source) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(upgradeId, "upgradeId");
        Objects.requireNonNull(beneficiaryId, "beneficiaryId");
        Objects.requireNonNull(targetScope, "targetScope");
        Objects.requireNonNull(purchaserId, "purchaserId");
        Objects.requireNonNull(source, "source");

        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                upsertCurrentLevel(connection, providerId, upgradeId, beneficiaryId, targetScope, level, now);
                insertPurchase(connection, providerId, upgradeId, beneficiaryId, targetScope,
                        level, purchaserId, source, now);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw new IllegalStateException(
                        "Failed to record upgrade purchase for " + beneficiaryId + " and " + upgradeId, e);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to record upgrade purchase for " + beneficiaryId + " and " + upgradeId, e);
        }
    }

    public List<UpgradePurchaseRecord> history(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");

        String sql = """
                SELECT id, provider_id, upgrade_id, beneficiary_uuid, purchaser_uuid, target_scope,
                       purchased_level, source, affected_count, created_at
                FROM haven_upgrade_purchases
                WHERE beneficiary_uuid = ?
                ORDER BY created_at ASC, id ASC
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            List<UpgradePurchaseRecord> records = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(mapPurchase(resultSet));
                }
            }
            return records;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read upgrade purchase history for " + playerId, e);
        }
    }

    public void revoke(UUID playerId, String upgradeId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(upgradeId, "upgradeId");

        String sql = "DELETE FROM haven_upgrade_levels WHERE player_uuid = ? AND upgrade_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, upgradeId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to revoke current upgrade level for " + playerId + " and " + upgradeId, e);
        }
    }

    private void upsertCurrentLevel(Connection connection, String providerId, String upgradeId,
                                    UUID beneficiaryId, String targetScope, int level, long updatedAt)
            throws SQLException {
        String updateSql = """
                UPDATE haven_upgrade_levels
                SET provider_id = ?, current_level = ?, target_scope = ?, updated_at = ?
                WHERE player_uuid = ? AND upgrade_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
            statement.setString(1, providerId);
            statement.setInt(2, level);
            statement.setString(3, targetScope);
            statement.setLong(4, updatedAt);
            statement.setString(5, beneficiaryId.toString());
            statement.setString(6, upgradeId);
            if (statement.executeUpdate() > 0) {
                return;
            }
        }

        String insertSql = """
                INSERT INTO haven_upgrade_levels
                    (player_uuid, upgrade_id, provider_id, current_level, target_scope, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
            statement.setString(1, beneficiaryId.toString());
            statement.setString(2, upgradeId);
            statement.setString(3, providerId);
            statement.setInt(4, level);
            statement.setString(5, targetScope);
            statement.setLong(6, updatedAt);
            statement.executeUpdate();
        }
    }

    private void insertPurchase(Connection connection, String providerId, String upgradeId,
                                UUID beneficiaryId, String targetScope, int level,
                                UUID purchaserId, String source, long createdAt) throws SQLException {
        String sql = """
                INSERT INTO haven_upgrade_purchases
                    (id, provider_id, upgrade_id, beneficiary_uuid, purchaser_uuid, target_scope,
                     purchased_level, source, affected_count, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nextPurchaseId(connection));
            statement.setString(2, providerId);
            statement.setString(3, upgradeId);
            statement.setString(4, beneficiaryId.toString());
            statement.setString(5, purchaserId.toString());
            statement.setString(6, targetScope);
            statement.setInt(7, level);
            statement.setString(8, source);
            statement.setInt(9, 1);
            statement.setLong(10, createdAt);
            statement.executeUpdate();
        }
    }

    private long nextPurchaseId(Connection connection) throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 FROM haven_upgrade_purchases";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return 1L;
            }
            return resultSet.getLong(1);
        }
    }

    private UpgradePurchaseRecord mapPurchase(ResultSet resultSet) throws SQLException {
        return new UpgradePurchaseRecord(
                resultSet.getLong("id"),
                resultSet.getString("provider_id"),
                resultSet.getString("upgrade_id"),
                UUID.fromString(resultSet.getString("beneficiary_uuid")),
                UUID.fromString(resultSet.getString("purchaser_uuid")),
                resultSet.getString("target_scope"),
                resultSet.getInt("purchased_level"),
                resultSet.getString("source"),
                resultSet.getInt("affected_count"),
                resultSet.getLong("created_at")
        );
    }
}
