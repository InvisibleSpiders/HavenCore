package dev.invisiblespiders.haven.api.event;

import dev.invisiblespiders.haven.api.model.VirtualInventory;

import java.util.UUID;

public class HavenVirtualStorageOpenEvent extends HavenEvent {
    private final UUID playerUuid;
    private final VirtualInventory inventory;
    public HavenVirtualStorageOpenEvent(UUID playerUuid, VirtualInventory inventory) {
        this.playerUuid = playerUuid;
        this.inventory = inventory;
    }
    public UUID getPlayerUuid() { return playerUuid; }
    public VirtualInventory getInventory() { return inventory; }
}
