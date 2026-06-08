package dev.invisiblespiders.haven.core.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.invisiblespiders.haven.api.model.PlayerCodex;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CodexRepositoryTest {

    private static HikariDataSource ds;
    private static CodexRepository repo;

    private static final UUID PLAYER = UUID.randomUUID();

    @BeforeAll
    static void setup() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite::memory:");
        cfg.setMaximumPoolSize(1);
        ds = new HikariDataSource(cfg);

        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migrations/haven")
            .table("flyway_schema_history_haven")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load()
            .migrate();

        repo = new CodexRepository(ds);
    }

    @AfterAll
    static void teardown() { ds.close(); }

    @Test
    @Order(1)
    void emptyCodexOnFirstLoad() throws Exception {
        PlayerCodex codex = repo.load(PLAYER);
        assertEquals(0, codex.getTotalDiscoveries());
    }

    @Test
    @Order(2)
    void insertNewDiscovery() throws Exception {
        boolean inserted = repo.insert(PLAYER, "mobs:creeper", System.currentTimeMillis());
        assertTrue(inserted);
    }

    @Test
    @Order(3)
    void duplicateInsertIgnored() throws Exception {
        boolean inserted = repo.insert(PLAYER, "mobs:creeper", System.currentTimeMillis());
        assertFalse(inserted);
    }

    @Test
    @Order(4)
    void loadReflectsDiscoveries() throws Exception {
        repo.insert(PLAYER, "mobs:zombie", System.currentTimeMillis());
        PlayerCodex codex = repo.load(PLAYER);
        assertTrue(codex.hasDiscovered("mobs:creeper"));
        assertTrue(codex.hasDiscovered("mobs:zombie"));
        assertEquals(2, codex.getTotalDiscoveries());
    }

    @Test
    @Order(5)
    void countByCategoryOnlyCountsCategoryPrefix() throws Exception {
        repo.insert(PLAYER, "ores:diamond", System.currentTimeMillis());
        assertEquals(2, repo.countByCategory(PLAYER, "mobs"));
        assertEquals(1, repo.countByCategory(PLAYER, "ores"));
        assertEquals(0, repo.countByCategory(PLAYER, "fish"));
    }
}
