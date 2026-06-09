package dev.invisiblespiders.haven.core.service;

import dev.invisiblespiders.haven.api.event.HavenEvent;
import dev.invisiblespiders.haven.api.model.VirtualInventory;
import dev.invisiblespiders.haven.api.service.HavenEventBus;
import dev.invisiblespiders.haven.core.config.StorageSettings;
import dev.invisiblespiders.haven.core.gui.VirtualInventoryHolder;
import dev.invisiblespiders.haven.core.repository.VirtualInventoryRepository;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StorageServiceImplTest {

    @Test
    void createWithoutRowsUsesConfiguredDefaultRows() throws SQLException {
        VirtualInventoryRepository repo = mock(VirtualInventoryRepository.class);
        StorageServiceImpl service = newService(mock(Plugin.class), repo, new StorageSettings(4, 0));
        UUID ownerUuid = UUID.randomUUID();

        VirtualInventory created = service.create(ownerUuid, "Workshop").join();

        assertEquals(4, created.getRows());
        ArgumentCaptor<VirtualInventory> inventoryCaptor = ArgumentCaptor.forClass(VirtualInventory.class);
        verify(repo).save(inventoryCaptor.capture(), eq(0));
        assertEquals(4, inventoryCaptor.getValue().getRows());
    }

    @Test
    void createRejectsRowsOutsideInventoryBounds() {
        StorageServiceImpl service = newService(mock(Plugin.class));

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> service.create(UUID.randomUUID(), "Too Large", 7)
        );

        assertTrue(error.getMessage().contains("between 1 and 6"));
    }

    @Test
    void createFailsWhenOwnerHasReachedConfiguredInventoryLimit() throws SQLException {
        VirtualInventoryRepository repo = mock(VirtualInventoryRepository.class);
        UUID ownerUuid = UUID.randomUUID();
        doThrow(new IllegalStateException("Player has reached the maximum virtual inventory limit."))
            .when(repo).save(any(VirtualInventory.class), eq(2));
        StorageServiceImpl service = newService(mock(Plugin.class), repo, new StorageSettings(3, 2));

        CompletionException error = assertThrows(
            CompletionException.class,
            () -> service.create(ownerUuid, "Overflow", 3).join()
        );

        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertTrue(error.getCause().getMessage().contains("maximum"));
    }

    @Test
    void openSchedulesInventoryWorkWhenCalledOffPrimaryThread() {
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        Server server = mock(Server.class);
        when(server.getScheduler()).thenReturn(scheduler);
        Plugin plugin = mock(Plugin.class);
        when(plugin.getServer()).thenReturn(server);

        StorageServiceImpl service = newService(plugin);
        Player player = mock(Player.class);
        VirtualInventory inventory = inventory();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);

            service.open(player, inventory);

            verify(scheduler).runTask(eq(plugin), any(Runnable.class));
            verify(player, never()).openInventory(any(Inventory.class));
            bukkit.verify(() -> Bukkit.createInventory(
                any(InventoryHolder.class),
                anyInt(),
                any(Component.class)
            ), never());
        }
    }

    @Test
    void openCreatesInventoryImmediatelyOnPrimaryThreadAndSetsHolderInventory() {
        Plugin plugin = mock(Plugin.class);
        StorageServiceImpl service = newService(plugin);
        Player player = mock(Player.class);
        VirtualInventory virtualInventory = inventory();
        Inventory bukkitInventory = mock(Inventory.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.createInventory(
                any(InventoryHolder.class),
                eq(virtualInventory.getSize()),
                any(Component.class)
            )).thenReturn(bukkitInventory);

            service.open(player, virtualInventory);

            ArgumentCaptor<InventoryHolder> holderCaptor = ArgumentCaptor.forClass(InventoryHolder.class);
            bukkit.verify(() -> Bukkit.createInventory(
                holderCaptor.capture(),
                eq(virtualInventory.getSize()),
                any(Component.class)
            ));
            VirtualInventoryHolder holder = assertInstanceOf(VirtualInventoryHolder.class, holderCaptor.getValue());
            assertSame(bukkitInventory, holder.getInventory());
            verify(player).openInventory(bukkitInventory);
        }
    }

    private static StorageServiceImpl newService(Plugin plugin) {
        return newService(plugin, mock(VirtualInventoryRepository.class), StorageSettings.defaults());
    }

    private static StorageServiceImpl newService(Plugin plugin, VirtualInventoryRepository repo,
                                                 StorageSettings settings) {
        return new StorageServiceImpl(
            repo,
            new NoOpEventBus(),
            Runnable::run,
            plugin,
            Logger.getLogger(StorageServiceImplTest.class.getName()),
            settings
        );
    }

    private static VirtualInventory inventory() {
        return new VirtualInventory(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Vault",
            1,
            System.currentTimeMillis()
        );
    }

    private static final class NoOpEventBus implements HavenEventBus {
        @Override
        public <T extends HavenEvent> void subscribe(Class<T> type, Handler<T> handler) {}

        @Override
        public <T extends HavenEvent> void unsubscribe(Class<T> type, Handler<T> handler) {}

        @Override
        public <T extends HavenEvent> void publish(T event) {}
    }
}
