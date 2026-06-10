package dev.invisiblespiders.haven.api.service;

import dev.invisiblespiders.haven.api.model.VirtualInventory;
import dev.invisiblespiders.haven.api.exception.VirtualInventoryLimitException;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface HavenStorageService {

    /**
     * Creates a virtual inventory for the owner.
     *
     * @throws VirtualInventoryLimitException as the completion cause when the owner has reached the configured limit
     */
    CompletableFuture<VirtualInventory> create(UUID ownerUuid, String name);

    /**
     * Creates a virtual inventory with a specific row count.
     *
     * @throws VirtualInventoryLimitException as the completion cause when the owner has reached the configured limit
     */
    CompletableFuture<VirtualInventory> create(UUID ownerUuid, String name, int rows);

    CompletableFuture<Optional<VirtualInventory>> get(UUID id);

    CompletableFuture<List<VirtualInventory>> getByOwner(UUID ownerUuid);

    /** Opens the virtual inventory GUI for the player, scheduling onto the main thread when needed. */
    void open(Player player, VirtualInventory inventory);

    CompletableFuture<Void> save(VirtualInventory inventory);

    CompletableFuture<Void> delete(UUID id);
}
