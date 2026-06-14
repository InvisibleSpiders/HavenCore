package dev.invisiblespiders.haven.core.reward;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.invisiblespiders.haven.api.reward.RewardClaimResult;
import dev.invisiblespiders.haven.api.reward.RewardDefinition;
import dev.invisiblespiders.haven.api.reward.RewardHandler;
import dev.invisiblespiders.haven.api.reward.RewardProvider;
import dev.invisiblespiders.haven.api.reward.RewardRecord;
import dev.invisiblespiders.haven.api.reward.RewardStatus;
import dev.invisiblespiders.haven.core.db.SqlMigrator;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RewardServiceImplTest {

    private HikariDataSource dataSource;
    private RewardRepository repository;
    private Player player;
    private Instant now;

    @BeforeEach
    void setup() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite::memory:");
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        SqlMigrator.migrate(dataSource, "haven", "db/migrations/haven", getClass().getClassLoader());
        repository = new RewardRepository(dataSource);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        now = Instant.now();
    }

    @AfterEach
    void teardown() {
        dataSource.close();
    }

    @Test
    void duplicateProviderIdRejected() {
        RewardServiceImpl service = serviceWith(new TestRewardProvider("test", true));

        assertThrows(IllegalArgumentException.class,
                () -> service.registerProvider(new TestRewardProvider("test", true)));
    }

    @Test
    void claimDelegatesToProviderThenMarksClaimed() {
        RewardProvider provider = new TestRewardProvider("test", true);
        RewardServiceImpl service = serviceWith(provider);
        RewardRecord record = service.enqueue(player.getUniqueId(), "test", "crate-key",
                "Crate Key", Map.of("crate", "vote"), now.plusSeconds(3600));

        RewardClaimResult result = service.claim(player, record.id());

        assertTrue(result.succeeded());
        assertEquals(RewardStatus.CLAIMED, repository.find(record.id()).orElseThrow().status());
    }

    @Test
    void differentPlayerCannotClaimRewardAndHandlerIsNotCalled() {
        TestRewardHandler handler = TestRewardHandler.succeeding("crate-key");
        RewardServiceImpl service = serviceWith(new TestRewardProvider("test", handler));
        RewardRecord record = service.enqueue(player.getUniqueId(), "test", "crate-key",
                "Crate Key", Map.of("crate", "vote"), now.plusSeconds(3600));
        Player otherPlayer = mock(Player.class);
        when(otherPlayer.getUniqueId()).thenReturn(UUID.randomUUID());

        RewardClaimResult result = service.claim(otherPlayer, record.id());

        assertFalse(result.succeeded());
        assertEquals("reward-unavailable", result.code());
        assertEquals(0, handler.calls());
        assertEquals(RewardStatus.PENDING, repository.find(record.id()).orElseThrow().status());
    }

    @Test
    void concurrentClaimsOnlyInvokeHandlerOnceAndOnlyOneSucceeds() throws Exception {
        BlockingRewardHandler handler = new BlockingRewardHandler("crate-key");
        RewardServiceImpl service = serviceWith(new TestRewardProvider("test", handler));
        RewardRecord record = service.enqueue(player.getUniqueId(), "test", "crate-key",
                "Crate Key", Map.of("crate", "vote"), now.plusSeconds(3600));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        try {
            Future<RewardClaimResult> first = executor.submit(() -> claimAfterStart(service, record.id(), startGate));
            Future<RewardClaimResult> second = executor.submit(() -> claimAfterStart(service, record.id(), startGate));

            startGate.countDown();
            assertTrue(handler.firstEntered.await(5, TimeUnit.SECONDS), "first claim did not reach handler");
            Thread.sleep(100);
            handler.release.countDown();
            RewardClaimResult firstResult = first.get(5, TimeUnit.SECONDS);
            RewardClaimResult secondResult = second.get(5, TimeUnit.SECONDS);

            int successes = (firstResult.succeeded() ? 1 : 0) + (secondResult.succeeded() ? 1 : 0);
            int failures = (!firstResult.succeeded() ? 1 : 0) + (!secondResult.succeeded() ? 1 : 0);
            RewardClaimResult failure = firstResult.succeeded() ? secondResult : firstResult;
            assertEquals(1, handler.calls());
            assertEquals(1, successes);
            assertEquals(1, failures);
            assertEquals("reward-unavailable", failure.code());
            assertEquals(RewardStatus.CLAIMED, repository.find(record.id()).orElseThrow().status());
        } finally {
            handler.release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void missingProviderFailureDoesNotMarkClaimed() {
        RewardServiceImpl service = serviceWith();
        RewardRecord record = service.enqueue(player.getUniqueId(), "missing", "crate-key",
                "Crate Key", Map.of("crate", "vote"), now.plusSeconds(3600));

        RewardClaimResult result = service.claim(player, record.id());

        assertFalse(result.succeeded());
        assertEquals("provider-unavailable", result.code());
        assertEquals(RewardStatus.PENDING, repository.find(record.id()).orElseThrow().status());
    }

    @Test
    void missingHandlerFailureDoesNotMarkClaimed() {
        RewardServiceImpl service = serviceWith(new TestRewardProvider("test", false));
        RewardRecord record = service.enqueue(player.getUniqueId(), "test", "crate-key",
                "Crate Key", Map.of("crate", "vote"), now.plusSeconds(3600));

        RewardClaimResult result = service.claim(player, record.id());

        assertFalse(result.succeeded());
        assertEquals("reward-unavailable", result.code());
        assertEquals(RewardStatus.PENDING, repository.find(record.id()).orElseThrow().status());
    }

    @Test
    void providerHandlerFailureDoesNotMarkClaimed() {
        TestRewardHandler handler = TestRewardHandler.failing("crate-key");
        RewardServiceImpl service = serviceWith(new TestRewardProvider("test", handler));
        RewardRecord record = service.enqueue(player.getUniqueId(), "test", "crate-key",
                "Crate Key", Map.of("crate", "vote"), now.plusSeconds(3600));

        RewardClaimResult result = service.claim(player, record.id());

        assertFalse(result.succeeded());
        assertEquals("handler-failed", result.code());
        assertEquals(1, handler.calls());
        assertEquals(RewardStatus.PENDING, repository.find(record.id()).orElseThrow().status());
    }

    @Test
    void expiredRewardFailureDoesNotCallHandlerAndIsNotClaimed() {
        TestRewardHandler handler = TestRewardHandler.succeeding("crate-key");
        RewardServiceImpl service = serviceWith(new TestRewardProvider("test", handler));
        RewardRecord record = service.enqueue(player.getUniqueId(), "test", "crate-key",
                "Crate Key", Map.of("crate", "vote"), Instant.EPOCH);

        RewardClaimResult result = service.claim(player, record.id());

        assertFalse(result.succeeded());
        assertEquals("reward-expired", result.code());
        assertEquals(0, handler.calls());
        assertEquals(RewardStatus.EXPIRED, repository.find(record.id()).orElseThrow().status());
    }

    @Test
    void missingRewardFailure() {
        RewardServiceImpl service = serviceWith(new TestRewardProvider("test", true));

        RewardClaimResult result = service.claim(player, 999L);

        assertFalse(result.succeeded());
        assertEquals("missing-reward", result.code());
    }

    @Test
    void pendingReturnsRepositoryPendingRewards() {
        RewardServiceImpl service = serviceWith(new TestRewardProvider("test", true));
        RewardRecord first = service.enqueue(player.getUniqueId(), "test", "crate-key",
                "First Key", Map.of("crate", "vote"), now.plusSeconds(3600));
        RewardRecord second = service.enqueue(player.getUniqueId(), "test", "crate-key",
                "Second Key", Map.of("crate", "daily"), now.plusSeconds(3600));
        service.enqueue(UUID.randomUUID(), "test", "crate-key",
                "Other Key", Map.of("crate", "vote"), now.plusSeconds(3600));

        assertEquals(List.of(first, second), service.pending(player.getUniqueId()));
    }

    @Test
    void revokeDelegatesAndChangesPendingRewardToRevoked() {
        RewardServiceImpl service = serviceWith(new TestRewardProvider("test", true));
        RewardRecord record = service.enqueue(player.getUniqueId(), "test", "crate-key",
                "Crate Key", Map.of("crate", "vote"), now.plusSeconds(3600));

        Optional<RewardRecord> result = service.revoke(record.id(), "admin");

        assertTrue(result.isPresent());
        assertEquals(RewardStatus.REVOKED, result.orElseThrow().status());
        assertEquals(RewardStatus.REVOKED, repository.find(record.id()).orElseThrow().status());
    }

    private RewardServiceImpl serviceWith(RewardProvider... providers) {
        RewardServiceImpl service = new RewardServiceImpl(repository);
        for (RewardProvider provider : providers) {
            service.registerProvider(provider);
        }
        return service;
    }

    private RewardClaimResult claimAfterStart(RewardServiceImpl service, long rewardId, CountDownLatch startGate)
            throws InterruptedException {
        assertTrue(startGate.await(5, TimeUnit.SECONDS));
        return service.claim(player, rewardId);
    }

    private static final class TestRewardProvider implements RewardProvider {
        private final String id;
        private final RewardHandler handler;

        private TestRewardProvider(String id, boolean hasHandler) {
            this(id, hasHandler ? TestRewardHandler.succeeding("crate-key") : null);
        }

        private TestRewardProvider(String id, RewardHandler handler) {
            this.id = id;
            this.handler = handler;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String displayName() {
            return "Test";
        }

        @Override
        public List<RewardDefinition> rewards() {
            return List.of();
        }

        @Override
        public Optional<RewardHandler> handler(String rewardType) {
            return handler != null && handler.rewardType().equals(rewardType)
                    ? Optional.of(handler)
                    : Optional.empty();
        }
    }

    private static final class TestRewardHandler implements RewardHandler {
        private final String rewardType;
        private final RewardClaimResult result;
        private final AtomicInteger calls = new AtomicInteger();

        private TestRewardHandler(String rewardType, RewardClaimResult result) {
            this.rewardType = rewardType;
            this.result = result;
        }

        private static TestRewardHandler succeeding(String rewardType) {
            return new TestRewardHandler(rewardType, RewardClaimResult.success("Claimed test reward."));
        }

        private static TestRewardHandler failing(String rewardType) {
            return new TestRewardHandler(rewardType,
                    RewardClaimResult.failure("handler-failed", "Handler rejected reward."));
        }

        @Override
        public String rewardType() {
            return rewardType;
        }

        @Override
        public RewardClaimResult claim(Player player, RewardRecord reward) {
            calls.incrementAndGet();
            return result;
        }

        private int calls() {
            return calls.get();
        }
    }

    private static final class BlockingRewardHandler implements RewardHandler {
        private final String rewardType;
        private final CountDownLatch firstEntered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger calls = new AtomicInteger();

        private BlockingRewardHandler(String rewardType) {
            this.rewardType = rewardType;
        }

        @Override
        public String rewardType() {
            return rewardType;
        }

        @Override
        public RewardClaimResult claim(Player player, RewardRecord reward) {
            calls.incrementAndGet();
            firstEntered.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return RewardClaimResult.failure("interrupted", "Interrupted.");
            }
            return RewardClaimResult.success("Claimed test reward.");
        }

        private int calls() {
            return calls.get();
        }
    }
}
