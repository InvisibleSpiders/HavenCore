package dev.invisiblespiders.haven.core.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

/** InventoryHolder that carries slot->handler mappings for GUI click routing. */
public class GuiHolder implements InventoryHolder {

    private final Map<Integer, GuiClickHandler> handlers;
    private Inventory inventory;

    public GuiHolder(Map<Integer, GuiClickHandler> handlers) {
        this.handlers = handlers;
    }

    public void handleClick(int slot, org.bukkit.event.inventory.InventoryClickEvent event) {
        event.setCancelled(true);
        GuiClickHandler handler = handlers.get(slot);
        if (handler != null) handler.onClick(event);
    }

    @Override
    public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }
}
