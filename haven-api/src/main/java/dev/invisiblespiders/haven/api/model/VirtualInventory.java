package dev.invisiblespiders.haven.api.model;

import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VirtualInventory {

    private final UUID id;
    private final UUID ownerUuid;
    private String name;
    private final int rows;
    private final long createdAt;
    // slot -> ItemStack (null = empty)
    private final Map<Integer, ItemStack> contents = new HashMap<>();

    public VirtualInventory(UUID id, UUID ownerUuid, String name, int rows, long createdAt) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.name = name;
        this.rows = rows;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public String getName() { return name; }
    public int getRows() { return rows; }
    public int getSize() { return rows * 9; }
    public long getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }

    public ItemStack getItem(int slot) { return contents.get(slot); }

    public void setItem(int slot, ItemStack item) {
        if (slot < 0 || slot >= getSize()) throw new IndexOutOfBoundsException("Slot " + slot);
        if (item == null || item.getType().isAir()) contents.remove(slot);
        else contents.put(slot, item);
    }

    public Map<Integer, ItemStack> getContents() { return Map.copyOf(contents); }

    public void loadContents(Map<Integer, ItemStack> items) {
        contents.clear();
        contents.putAll(items);
    }
}
