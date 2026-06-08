package dev.invisiblespiders.haven.core.gui;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** Builds a simple, non-paginated chest GUI. */
public class GuiBuilder {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final int rows;
    private final String title; // MiniMessage
    private final Map<Integer, ItemStack> items = new HashMap<>();
    private final Map<Integer, GuiClickHandler> handlers = new HashMap<>();

    public GuiBuilder(int rows, String title) {
        if (rows < 1 || rows > 6) throw new IllegalArgumentException("rows must be 1-6");
        this.rows = rows;
        this.title = title;
    }

    public GuiBuilder slot(int index, ItemStack item) {
        items.put(index, item);
        return this;
    }

    public GuiBuilder slot(int index, ItemStack item, GuiClickHandler onClick) {
        items.put(index, item);
        handlers.put(index, onClick);
        return this;
    }

    /** row and col are 0-based. */
    public GuiBuilder slot(int row, int col, ItemStack item, GuiClickHandler onClick) {
        return slot(row * 9 + col, item, onClick);
    }

    public Inventory build() {
        GuiHolder holder = new GuiHolder(handlers);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, MM.deserialize(title));
        items.forEach(inv::setItem);
        holder.setInventory(inv);
        return inv;
    }
}
