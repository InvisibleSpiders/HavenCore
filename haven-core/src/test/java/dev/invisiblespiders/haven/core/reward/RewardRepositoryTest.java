package dev.invisiblespiders.haven.core.reward;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.invisiblespiders.haven.api.reward.RewardRecord;
import dev.invisiblespiders.haven.api.reward.RewardStatus;
import dev.invisiblespiders.haven.core.db.SqlMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardRepositoryTest {

    private HikariDataSource dataSource;
    private RewardRepository repository;

    @BeforeEach
    void setup() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite::memory:");
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        SqlMigrator.migrate(dataSource, "haven", "db/migrations/haven", getClass().getClassLoader());
        repository = new RewardRepository(dataSource);
    }

    @AfterEach
    void teardown() {
        dataSource.close();
    }

    @Test
    void enqueueReturnsPendingRecordWithPayloadRoundTripped() {
        UUID playerId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2026-06-14T12:05:00Z");
        Map<String, String> payload = Map.of("material", "DIAMOND", "amount", "3");

        RewardRecord reward = repository.enqueue(playerId, "havenvault", "server-bank-item",
                "Item Bundle", payload, expiresAt, "test");

        assertEquals(1L, reward.id());
        assertEquals("havenvault", reward.providerId());
        assertEquals("server-bank-item", reward.rewardType());
        assertEquals(playerId, reward.playerId());
        assertEquals("Item Bundle", reward.displayText());
        assertEquals(payload, reward.payload());
        assertEquals(RewardStatus.PENDING, reward.status());
        assertEquals(expiresAt, reward.expiresAt());
        assertEquals(reward, repository.find(reward.id()).orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> reward.payload().put("extra", "value"));
    }

    @Test
    void pendingExcludesClaimedAndExpiredRewards() {
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-14T12:00:00Z");
        RewardRecord pending = repository.enqueue(playerId, "havenvault", "server-bank-item",
                "Item Bundle", Map.of("material", "DIAMOND"), now.plusSeconds(60), "test");
        RewardRecord expired = repository.enqueue(playerId, "havenvault", "server-bank-item",
                "Expired Bundle", Map.of("material", "DIRT"), now.minusSeconds(60), "test");

        repository.claim(pending.id(), now);
        repository.expire(now);

        assertTrue(repository.pending(playerId, now).isEmpty());
        assertEquals(RewardStatus.CLAIMED, repository.find(pending.id()).orElseThrow().status());
        assertEquals(RewardStatus.EXPIRED, repository.find(expired.id()).orElseThrow().status());
    }

    @Test
    void pendingExcludesExpiredRewardBeforeExpireRuns() {
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-14T12:00:00Z");
        RewardRecord expiredPending = repository.enqueue(playerId, "havenvault", "server-bank-item",
                "Expired Pending Bundle", Map.of("material", "DIRT"), now, "test");
        RewardRecord available = repository.enqueue(playerId, "havenvault", "server-bank-item",
                "Available Bundle", Map.of("material", "DIAMOND"), now.plusSeconds(60), "test");

        assertEquals(List.of(available), repository.pending(playerId, now));
        assertEquals(RewardStatus.PENDING, repository.find(expiredPending.id()).orElseThrow().status());
    }

    @Test
    void noExpiryRewardRemainsPending() {
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-14T12:00:00Z");
        RewardRecord reward = repository.enqueue(playerId, "havenvault", "server-bank-item",
                "Permanent Bundle", Map.of("material", "EMERALD"), null, "test");

        assertEquals(List.of(reward), repository.pending(playerId, now));
        assertEquals(0, repository.expire(now));
        assertEquals(RewardStatus.PENDING, repository.find(reward.id()).orElseThrow().status());
    }

    @Test
    void claimPendingRewardReturnsClaimedRecordWithClaimedAt() {
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-14T12:00:00Z");
        RewardRecord reward = repository.enqueue(playerId, "havenvault", "server-bank-item",
                "Item Bundle", Map.of("material", "DIAMOND"), now.plusSeconds(60), "test");

        Optional<RewardRecord> claimed = repository.claim(reward.id(), now);

        assertTrue(claimed.isPresent());
        assertEquals(RewardStatus.CLAIMED, claimed.orElseThrow().status());
        assertEquals(now, claimed.orElseThrow().claimedAt());
        assertEquals(RewardStatus.CLAIMED, repository.find(reward.id()).orElseThrow().status());
    }

    @Test
    void claimMissingRewardReturnsEmpty() {
        assertTrue(repository.claim(999L, Instant.parse("2026-06-14T12:00:00Z")).isEmpty());
    }

    @Test
    void claimNonPendingRewardReturnsEmptyAndDoesNotChangeReward() {
        UUID playerId = UUID.randomUUID();
        Instant firstClaimedAt = Instant.parse("2026-06-14T12:00:00Z");
        Instant secondClaimedAt = firstClaimedAt.plusSeconds(60);
        RewardRecord reward = repository.enqueue(playerId, "havenvault", "server-bank-item",
                "Item Bundle", Map.of("material", "DIAMOND"), secondClaimedAt.plusSeconds(60), "test");

        repository.claim(reward.id(), firstClaimedAt);

        assertTrue(repository.claim(reward.id(), secondClaimedAt).isEmpty());
        RewardRecord unchanged = repository.find(reward.id()).orElseThrow();
        assertEquals(RewardStatus.CLAIMED, unchanged.status());
        assertEquals(firstClaimedAt, unchanged.claimedAt());
    }

    @Test
    void claimExpiredPendingRewardReturnsEmptyUntilExpirySweep() {
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-14T12:00:00Z");
        RewardRecord reward = repository.enqueue(playerId, "havenvault", "server-bank-item",
                "Expired Bundle", Map.of("material", "DIRT"), now, "test");

        assertTrue(repository.claim(reward.id(), now).isEmpty());
        assertEquals(RewardStatus.PENDING, repository.find(reward.id()).orElseThrow().status());
        assertEquals(1, repository.expire(now));
        assertEquals(RewardStatus.EXPIRED, repository.find(reward.id()).orElseThrow().status());
    }

    @Test
    void revokeChangesStatusOnlyForPendingReward() {
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-14T12:00:00Z");
        RewardRecord pending = repository.enqueue(playerId, "havenvault", "server-bank-item",
                "Pending Bundle", Map.of("material", "GOLD_INGOT"), now.plusSeconds(60), "test");
        RewardRecord claimed = repository.enqueue(playerId, "havenvault", "server-bank-item",
                "Claimed Bundle", Map.of("material", "IRON_INGOT"), now.plusSeconds(60), "test");

        repository.claim(claimed.id(), now);

        assertEquals(RewardStatus.REVOKED, repository.revoke(pending.id(), "admin").orElseThrow().status());
        assertTrue(repository.revoke(claimed.id(), "admin").isEmpty());
        assertEquals(RewardStatus.CLAIMED, repository.find(claimed.id()).orElseThrow().status());
        assertTrue(repository.revoke(999L, "admin").isEmpty());
    }

    @Test
    void expireCountsOnlyPendingRewardsAtOrBeforeNow() {
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-14T12:00:00Z");
        RewardRecord expiresNow = repository.enqueue(playerId, "havenvault", "server-bank-item",
                "Expires Now", Map.of("material", "DIRT"), now, "test");
        RewardRecord expiresBefore = repository.enqueue(playerId, "havenvault", "server-bank-item",
                "Expires Before", Map.of("material", "COBBLESTONE"), now.minusMillis(1), "test");
        RewardRecord expiresAfter = repository.enqueue(playerId, "havenvault", "server-bank-item",
                "Expires After", Map.of("material", "DIAMOND"), now.plusMillis(1), "test");
        RewardRecord alreadyNonPending = repository.enqueue(playerId, "havenvault", "server-bank-item",
                "Already Claimed", Map.of("material", "EMERALD"), now.minusMillis(1), "test");
        repository.claim(alreadyNonPending.id(), now.minusSeconds(1));

        assertEquals(2, repository.expire(now));

        assertEquals(RewardStatus.EXPIRED, repository.find(expiresNow.id()).orElseThrow().status());
        assertEquals(RewardStatus.EXPIRED, repository.find(expiresBefore.id()).orElseThrow().status());
        assertEquals(RewardStatus.PENDING, repository.find(expiresAfter.id()).orElseThrow().status());
        assertEquals(RewardStatus.CLAIMED, repository.find(alreadyNonPending.id()).orElseThrow().status());
        assertEquals(0, repository.expire(now));
    }

    @Test
    void payloadEscapingHandlesBackslashEqualsAndNewline() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("key\\=one\nline", "value\\=two\nline");

        String serialized = RewardRepository.serializePayload(payload);

        assertFalse(serialized.contains("\n"));
        assertTrue(serialized.contains("\\\\"));
        assertTrue(serialized.contains("\\="));
        assertTrue(serialized.contains("\\n"));
        assertEquals(payload, RewardRepository.deserializePayload(serialized));
    }

    @Test
    void pendingRewardsAreOrderedByCreatedAtThenId() {
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-14T12:00:00Z");

        RewardRecord first = repository.enqueue(playerId, "havenvault", "server-bank-item",
                "First Bundle", Map.of("order", "first"), now.plusSeconds(60), "first");
        RewardRecord second = repository.enqueue(playerId, "havenvault", "server-bank-item",
                "Second Bundle", Map.of("order", "second"), now.plusSeconds(60), "second");
        RewardRecord otherPlayer = repository.enqueue(UUID.randomUUID(), "havenvault", "server-bank-item",
                "Other Bundle", Map.of("order", "other"), now.plusSeconds(60), "other");

        assertEquals(List.of(first, second), repository.pending(playerId, now));
        assertEquals(List.of(otherPlayer), repository.pending(otherPlayer.playerId(), now));
    }
}
