package dev.invisiblespiders.haven.core.gui;

import org.bukkit.event.inventory.InventoryClickEvent;

@FunctionalInterface
public interface GuiClickHandler {
    void onClick(InventoryClickEvent event);
}
