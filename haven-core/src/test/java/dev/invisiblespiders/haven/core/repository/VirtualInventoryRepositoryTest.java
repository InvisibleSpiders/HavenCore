package dev.invisiblespiders.haven.core.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.invisiblespiders.haven.api.model.VirtualInventory;
import dev.invisiblespiders.haven.core.db.SqlMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VirtualInventoryRepositoryTest {

    private HikariDataSource dataSource;
    private VirtualInventoryRepository repo;

    @BeforeEach
    void setup() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite::memory:");
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        SqlMigrator.migrate(dataSource, "haven", "db/migrations/haven", getClass().getClassLoader());
        repo = new VirtualInventoryRepository(dataSource);
    }

    @AfterEach
    void teardown() {
        dataSource.close();
    }

    @Test
    void saveWithLimitAllowsUpdatingExistingInventoryAtLimit() throws Exception {
        UUID ownerUuid = UUID.randomUUID();
        UUID inventoryId = UUID.randomUUID();
        repo.save(new VirtualInventory(inventoryId, ownerUuid, "Vault", 1, 10L), 1);

        repo.save(new VirtualInventory(inventoryId, ownerUuid, "Workshop", 2, 10L), 1);

        VirtualInventory updated = repo.findById(inventoryId).orElseThrow();
        assertEquals("Workshop", updated.getName());
        assertEquals(2, updated.getRows());
    }
}
