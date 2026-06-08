package dev.invisiblespiders.haven.core.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.invisiblespiders.haven.api.model.HavenPlayer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlayerRepositoryTest {

    private static HikariDataSource ds;
    private static PlayerRepository repo;

    @BeforeAll
    static void setup() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite::memory:");
        cfg.setMaximumPoolSize(1);
        cfg.setConnectionInitSql("PRAGMA foreign_keys=ON;");
        ds = new HikariDataSource(cfg);

        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migrations/haven")
            .table("flyway_schema_history_haven")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load()
            .migrate();

        repo = new PlayerRepository(ds);
    }

    @AfterAll
    static void teardown() { ds.close(); }

    private static final UUID UUID1 = UUID.randomUUID();

    @Test
    @Order(1)
    void notFoundBeforeInsert() throws Exception {
        assertTrue(repo.findByUuid(UUID1).isEmpty());
        assertTrue(repo.findByName("Steve").isEmpty());
    }

    @Test
    @Order(2)
    void upsertAndFind() throws Exception {
        long now = System.currentTimeMillis();
        HavenPlayer player = new HavenPlayer(UUID1, "Steve", now, now);
        repo.upsert(player);

        Optional<HavenPlayer> found = repo.findByUuid(UUID1);
        assertTrue(found.isPresent());
        assertEquals("Steve", found.get().getNameCache());
    }

    @Test
    @Order(3)
    void findByNameCaseInsensitive() throws Exception {
        Optional<HavenPlayer> found = repo.findByName("steve");
        assertTrue(found.isPresent());
    }

    @Test
    @Order(4)
    void saveAndLoadPluginData() throws Exception {
        HavenPlayer player = repo.findByUuid(UUID1).orElseThrow();
        player.setData("myplugin", "key1", "value1");
        player.setData("myplugin", "key2", "value2");
        repo.savePluginData(player, "myplugin");

        Optional<HavenPlayer> reloaded = repo.findByUuid(UUID1);
        assertTrue(reloaded.isPresent());
        assertEquals("value1", reloaded.get().getData("myplugin", "key1").orElse(null));
        assertEquals("value2", reloaded.get().getData("myplugin", "key2").orElse(null));
    }

    @Test
    @Order(5)
    void pluginDataIsolatedByPlugin() throws Exception {
        HavenPlayer player = repo.findByUuid(UUID1).orElseThrow();
        assertTrue(player.getData("otherplugin", "key1").isEmpty());
    }

    @Test
    @Order(6)
    void upsertUpdatesNameAndLastSeen() throws Exception {
        HavenPlayer player = repo.findByUuid(UUID1).orElseThrow();
        long newTime = System.currentTimeMillis() + 1000;
        player.setNameCache("SteveRenamed");
        player.setLastSeen(newTime);
        repo.upsert(player);

        HavenPlayer reloaded = repo.findByUuid(UUID1).orElseThrow();
        assertEquals("SteveRenamed", reloaded.getNameCache());
        assertEquals(newTime, reloaded.getLastSeen());
    }
}
