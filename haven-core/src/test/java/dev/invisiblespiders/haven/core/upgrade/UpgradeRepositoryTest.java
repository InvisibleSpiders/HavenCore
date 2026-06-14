package dev.invisiblespiders.haven.core.upgrade;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.invisiblespiders.haven.core.db.SqlMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpgradeRepositoryTest {

    private HikariDataSource dataSource;
    private UpgradeRepository repository;

    @BeforeEach
    void setup() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite::memory:");
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        SqlMigrator.migrate(dataSource, "haven", "db/migrations/haven", getClass().getClassLoader());
        repository = new UpgradeRepository(dataSource);
    }

    @AfterEach
    void teardown() {
        dataSource.close();
    }

    @Test
    void recordsAndReadsCurrentLevel() {
        UUID playerId = UUID.randomUUID();
        UUID purchaserId = UUID.randomUUID();

        repository.recordPurchase("havenvault", "havenvault:armory-slots", playerId,
                "PLAYER", 2, purchaserId, "test");

        assertEquals(2, repository.currentLevel(playerId, "havenvault:armory-slots"));
    }

    @Test
    void laterLevelReplacesCurrentLevelButKeepsHistory() {
        UUID playerId = UUID.randomUUID();

        repository.recordPurchase("havenvault", "havenvault:bank-cap", playerId,
                "PLAYER", 1, playerId, "first");
        repository.recordPurchase("havenvault", "havenvault:bank-cap", playerId,
                "PLAYER", 2, playerId, "second");

        assertEquals(2, repository.currentLevel(playerId, "havenvault:bank-cap"));
        assertEquals(2, repository.history(playerId).size());
    }

    @Test
    void missingCurrentLevelReturnsZero() {
        assertEquals(0, repository.currentLevel(UUID.randomUUID(), "havenvault:missing"));
    }

    @Test
    void revokeRemovesCurrentLevelButKeepsHistory() {
        UUID playerId = UUID.randomUUID();

        repository.recordPurchase("havenvault", "havenvault:bank-cap", playerId,
                "PLAYER", 1, playerId, "grant");
        repository.revoke(playerId, "havenvault:bank-cap");

        assertEquals(0, repository.currentLevel(playerId, "havenvault:bank-cap"));
        assertEquals(1, repository.history(playerId).size());
    }

    @Test
    void historyIsOrderedByCreationThenId() {
        UUID playerId = UUID.randomUUID();

        repository.recordPurchase("havenvault", "havenvault:bank-cap", playerId,
                "PLAYER", 1, playerId, "first");
        repository.recordPurchase("havenvault", "havenvault:armory-slots", playerId,
                "PLAYER", 3, playerId, "second");
        repository.recordPurchase("havenvault", "havenvault:bank-cap", playerId,
                "PLAYER", 2, playerId, "third");

        List<UpgradePurchaseRecord> history = repository.history(playerId);

        assertEquals(3, history.size());
        assertEquals("first", history.get(0).source());
        assertEquals("second", history.get(1).source());
        assertEquals("third", history.get(2).source());
        assertEquals(1, history.get(0).purchasedLevel());
        assertEquals(3, history.get(1).purchasedLevel());
        assertEquals(2, history.get(2).purchasedLevel());
    }
}
