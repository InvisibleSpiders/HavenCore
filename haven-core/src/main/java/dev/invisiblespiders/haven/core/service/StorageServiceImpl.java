package dev.invisiblespiders.haven.core.service;

import dev.invisiblespiders.haven.api.exception.VirtualInventoryLimitException;
import dev.invisiblespiders.haven.api.event.HavenVirtualStorageOpenEvent;
import dev.invisiblespiders.haven.api.model.VirtualInventory;
import dev.invisiblespiders.haven.api.service.HavenEventBus;
import dev.invisiblespiders.haven.api.service.HavenStorageService;
import dev.invisiblespiders.haven.core.config.StorageSettings;
import dev.invisiblespiders.haven.core.gui.VirtualInventoryHolder;
import dev.invisiblespiders.haven.core.repository.VirtualInventoryRepository;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

public class StorageServiceImpl implements HavenStorageService {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final VirtualInventoryRepository repo;
    private final HavenEventBus eventBus;
    private final Executor asyncExecutor;
    private final Plugin plugin;
    private final Logger logger;
    private final StorageSettings settings;

    public StorageServiceImpl(VirtualInventoryRepository repo, HavenEventBus eventBus,
                              Executor asyncExecutor, Plugin plugin, Logger logger) {
        this(repo, eventBus, asyncExecutor, plugin, logger, StorageSettings.defaults());
    }

    public StorageServiceImpl(VirtualInventoryRepository repo, HavenEventBus eventBus,
                              Executor asyncExecutor, Plugin plugin, Logger logger,
                              StorageSettings settings) {
        this.repo = repo;
        this.eventBus = eventBus;
        this.asyncExecutor = asyncExecutor;
        this.plugin = plugin;
        this.logger = logger;
        this.settings = settings;
    }

    @Override
    public CompletableFuture<VirtualInventory> create(UUID ownerUuid, String name) {
        return create(ownerUuid, name, settings.defaultRows());
    }

    @Override
    public CompletableFuture<VirtualInventory> create(UUID ownerUuid, String name, int rows) {
        if (!StorageSettings.isValidRows(rows)) {
            throw new IllegalArgumentException("Virtual inventory rows must be between 1 and 6.");
        }
        return CompletableFuture.supplyAsync(() -> {
            VirtualInventory inv = new VirtualInventory(
                UUID.randomUUID(), ownerUuid, name, rows, System.currentTimeMillis()
            );
            try {
                repo.save(inv, settings.maxPerPlayer());
            }
            catch (IllegalStateException e) {
                throw new VirtualInventoryLimitException(e.getMessage(), e);
            }
            catch (SQLException e) { throw new RuntimeException("Failed to create virtual inventory", e); }
            return inv;
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Optional<VirtualInventory>> get(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            try { return repo.findById(id); }
            catch (SQLException e) { throw new RuntimeException(e); }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<List<VirtualInventory>> getByOwner(UUID ownerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try { return repo.findByOwner(ownerUuid); }
            catch (SQLException e) { throw new RuntimeException(e); }
        }, asyncExecutor);
    }

    @Override
    public void open(Player player, VirtualInventory inv) {
        if (!Bukkit.isPrimaryThread()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> open(player, inv));
            return;
        }

        VirtualInventoryHolder holder = new VirtualInventoryHolder(inv, repo, asyncExecutor, logger);
        Inventory bukkit = Bukkit.createInventory(
            holder,
            inv.getSize(),
            MM.deserialize("<gold>" + inv.getName())
        );
        holder.setInventory(bukkit);
        inv.getContents().forEach(bukkit::setItem);
        player.openInventory(bukkit);
        eventBus.publish(new HavenVirtualStorageOpenEvent(player.getUniqueId(), inv));
    }

    @Override
    public CompletableFuture<Void> save(VirtualInventory inv) {
        return CompletableFuture.runAsync(() -> {
            try { repo.save(inv); }
            catch (SQLException e) { throw new RuntimeException(e); }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Void> delete(UUID id) {
        return CompletableFuture.runAsync(() -> {
            try { repo.delete(id); }
            catch (SQLException e) { throw new RuntimeException(e); }
        }, asyncExecutor);
    }
}
