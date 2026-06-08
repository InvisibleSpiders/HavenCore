package dev.invisiblespiders.haven.api.service;

import dev.invisiblespiders.haven.api.model.VirtualInventory;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface HavenStorageService {

    CompletableFuture<VirtualInventory> create(UUID ownerUuid, String name, int rows);

    CompletableFuture<Optional<VirtualInventory>> get(UUID id);

    CompletableFuture<List<VirtualInventory>> getByOwner(UUID ownerUuid);

    /** Opens the virtual inventory GUI for the player, scheduling onto the main thread when needed. */
    void open(Player player, VirtualInventory inventory);

    CompletableFuture<Void> save(VirtualInventory inventory);

    CompletableFuture<Void> delete(UUID id);
}
