package dev.invisiblespiders.haven.core.reward;

import dev.invisiblespiders.haven.api.reward.RewardRecord;
import dev.invisiblespiders.haven.api.reward.RewardStatus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RewardRepository {

    private final DataSource dataSource;

    public RewardRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public RewardRecord enqueue(UUID playerId, String providerId, String rewardType, String displayText,
                                Map<String, String> payload, Instant expiresAt, String source) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(rewardType, "rewardType");
        Objects.requireNonNull(displayText, "displayText");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(source, "source");

        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                long id = nextRewardId(connection);
                Instant createdAt = Instant.now();
                insertReward(connection, id, playerId, providerId, rewardType, displayText,
                        payload, createdAt, expiresAt, source);
                connection.commit();
                return find(connection, id).orElseThrow(() ->
                        new IllegalStateException("Inserted reward was not found: " + id));
            } catch (SQLException e) {
                connection.rollback();
                throw new IllegalStateException("Failed to enqueue reward for " + playerId, e);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to enqueue reward for " + playerId, e);
        }
    }

    public List<RewardRecord> pending(UUID playerId, Instant now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(now, "now");

        String sql = """
                SELECT id, provider_id, reward_type, player_uuid, display_text, payload, status,
                       created_at, expires_at, claimed_at
                FROM haven_rewards
                WHERE player_uuid = ?
                  AND status = ?
                  AND (expires_at IS NULL OR expires_at > ?)
                ORDER BY created_at ASC, id ASC
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, RewardStatus.PENDING.name());
            statement.setLong(3, now.toEpochMilli());
            List<RewardRecord> rewards = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rewards.add(mapReward(resultSet));
                }
            }
            return rewards;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read pending rewards for " + playerId, e);
        }
    }

    public Optional<RewardRecord> find(long rewardId) {
        try (Connection connection = dataSource.getConnection()) {
            return find(connection, rewardId);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find reward " + rewardId, e);
        }
    }

    public Optional<RewardRecord> claim(long rewardId, Instant claimedAt) {
        Objects.requireNonNull(claimedAt, "claimedAt");

        String sql = """
                UPDATE haven_rewards
                SET status = ?, claimed_at = ?
                WHERE id = ? AND status = ?
                  AND (expires_at IS NULL OR expires_at > ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, RewardStatus.CLAIMED.name());
            statement.setLong(2, claimedAt.toEpochMilli());
            statement.setLong(3, rewardId);
            statement.setString(4, RewardStatus.PENDING.name());
            statement.setLong(5, claimedAt.toEpochMilli());
            if (statement.executeUpdate() == 0) {
                return Optional.empty();
            }
            return find(connection, rewardId);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to claim reward " + rewardId, e);
        }
    }

    public Optional<RewardRecord> revoke(long rewardId, String source) {
        Objects.requireNonNull(source, "source");

        String sql = """
                UPDATE haven_rewards
                SET status = ?, source = ?
                WHERE id = ? AND status = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, RewardStatus.REVOKED.name());
            statement.setString(2, source);
            statement.setLong(3, rewardId);
            statement.setString(4, RewardStatus.PENDING.name());
            if (statement.executeUpdate() == 0) {
                return Optional.empty();
            }
            return find(connection, rewardId);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to revoke reward " + rewardId, e);
        }
    }

    public int expire(Instant now) {
        Objects.requireNonNull(now, "now");

        String sql = """
                UPDATE haven_rewards
                SET status = ?
                WHERE status = ? AND expires_at IS NOT NULL AND expires_at <= ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, RewardStatus.EXPIRED.name());
            statement.setString(2, RewardStatus.PENDING.name());
            statement.setLong(3, now.toEpochMilli());
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to expire rewards at " + now, e);
        }
    }

    static String serializePayload(Map<String, String> payload) {
        Objects.requireNonNull(payload, "payload");

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(escape(Objects.requireNonNull(entry.getKey(), "payload key")))
                    .append('=')
                    .append(escape(Objects.requireNonNull(entry.getValue(), "payload value")));
        }
        return builder.toString();
    }

    static Map<String, String> deserializePayload(String serialized) {
        Objects.requireNonNull(serialized, "serialized");

        Map<String, String> payload = new LinkedHashMap<>();
        if (serialized.isEmpty()) {
            return payload;
        }
        for (String line : serialized.split("\n", -1)) {
            int separator = firstUnescapedEquals(line);
            if (separator < 0) {
                throw new IllegalStateException("Invalid reward payload line without separator");
            }
            payload.put(unescape(line.substring(0, separator)), unescape(line.substring(separator + 1)));
        }
        return payload;
    }

    private void insertReward(Connection connection, long id, UUID playerId, String providerId,
                              String rewardType, String displayText, Map<String, String> payload,
                              Instant createdAt, Instant expiresAt, String source) throws SQLException {
        String sql = """
                INSERT INTO haven_rewards
                    (id, provider_id, reward_type, player_uuid, display_text, payload, status,
                     created_at, expires_at, claimed_at, source)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setString(2, providerId);
            statement.setString(3, rewardType);
            statement.setString(4, playerId.toString());
            statement.setString(5, displayText);
            statement.setString(6, serializePayload(payload));
            statement.setString(7, RewardStatus.PENDING.name());
            statement.setLong(8, createdAt.toEpochMilli());
            setNullableInstant(statement, 9, expiresAt);
            statement.setNull(10, Types.BIGINT);
            statement.setString(11, source);
            statement.executeUpdate();
        }
    }

    private long nextRewardId(Connection connection) throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 FROM haven_rewards";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return 1L;
            }
            return resultSet.getLong(1);
        }
    }

    private Optional<RewardRecord> find(Connection connection, long rewardId) throws SQLException {
        String sql = """
                SELECT id, provider_id, reward_type, player_uuid, display_text, payload, status,
                       created_at, expires_at, claimed_at
                FROM haven_rewards
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, rewardId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapReward(resultSet));
            }
        }
    }

    private RewardRecord mapReward(ResultSet resultSet) throws SQLException {
        return new RewardRecord(
                resultSet.getLong("id"),
                resultSet.getString("provider_id"),
                resultSet.getString("reward_type"),
                UUID.fromString(resultSet.getString("player_uuid")),
                resultSet.getString("display_text"),
                deserializePayload(resultSet.getString("payload")),
                RewardStatus.valueOf(resultSet.getString("status")),
                Instant.ofEpochMilli(resultSet.getLong("created_at")),
                nullableInstant(resultSet, "expires_at"),
                nullableInstant(resultSet, "claimed_at")
        );
    }

    private static void setNullableInstant(PreparedStatement statement, int index, Instant instant)
            throws SQLException {
        if (instant == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, instant.toEpochMilli());
        }
    }

    private static Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        if (resultSet.wasNull()) {
            return null;
        }
        return Instant.ofEpochMilli(value);
    }

    private static int firstUnescapedEquals(String line) {
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '=') {
                return i;
            }
        }
        return -1;
    }

    private static String escape(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') {
                builder.append("\\\\");
            } else if (c == '=') {
                builder.append("\\=");
            } else if (c == '\n') {
                builder.append("\\n");
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static String unescape(String value) {
        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!escaped) {
                if (c == '\\') {
                    escaped = true;
                } else {
                    builder.append(c);
                }
                continue;
            }

            if (c == 'n') {
                builder.append('\n');
            } else {
                builder.append(c);
            }
            escaped = false;
        }
        if (escaped) {
            builder.append('\\');
        }
        return builder.toString();
    }
}
