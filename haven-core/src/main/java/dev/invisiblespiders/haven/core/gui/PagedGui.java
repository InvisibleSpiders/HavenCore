package dev.invisiblespiders.haven.core.gui;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Paginated chest GUI. Items fill content slots; prev/next arrows auto-appear.
 * Content slots: rows 0 to (rows-2), all 9 columns. Bottom row is nav.
 */
public class PagedGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int NAV_PREV_SLOT = 45; // for 6-row GUI
    private static final int NAV_NEXT_SLOT = 53;

    private final String title; // MiniMessage
    private final List<ItemStack> content = new ArrayList<>();
    private final List<GuiClickHandler> contentHandlers = new ArrayList<>();
    private int page = 0;

    public PagedGui(String title) {
        this.title = title;
    }

    public PagedGui addItem(ItemStack item, GuiClickHandler onClick) {
        content.add(item);
        contentHandlers.add(onClick);
        return this;
    }

    public Inventory buildPage(int pageIndex) {
        int rows = 6;
        int contentSlots = (rows - 1) * 9; // 45 slots
        int totalPages = Math.max(1, (int) Math.ceil(content.size() / (double) contentSlots));
        this.page = Math.max(0, Math.min(pageIndex, totalPages - 1));
        int start = this.page * contentSlots;
        int end = Math.min(start + contentSlots, content.size());

        Map<Integer, GuiClickHandler> handlers = new HashMap<>();
        GuiHolder holder = new GuiHolder(handlers);
        Inventory inv = Bukkit.createInventory(holder, rows * 9,
            MM.deserialize(title + " <gray>(" + (this.page + 1) + "/" + totalPages + ")"));

        for (int i = start; i < end; i++) {
            int slot = i - start;
            inv.setItem(slot, content.get(i));
            GuiClickHandler ch = contentHandlers.get(i);
            if (ch != null) handlers.put(slot, ch);
        }

        // Prev arrow
        if (this.page > 0) {
            ItemStack prev = namedItem(Material.ARROW, "<yellow>← Previous");
            inv.setItem(NAV_PREV_SLOT, prev);
            int p = this.page;
            handlers.put(NAV_PREV_SLOT, e -> e.getWhoClicked().openInventory(buildPage(p - 1)));
        }
        // Next arrow
        if (this.page < totalPages - 1) {
            ItemStack next = namedItem(Material.ARROW, "<yellow>Next →");
            inv.setItem(NAV_NEXT_SLOT, next);
            int p = this.page;
            handlers.put(NAV_NEXT_SLOT, e -> e.getWhoClicked().openInventory(buildPage(p + 1)));
        }

        holder.setInventory(inv);
        return inv;
    }

    public void open(Player player) {
        player.openInventory(buildPage(0));
    }

    private ItemStack namedItem(Material mat, String miniName) {
        ItemStack item = new ItemStack(mat);
        item.editMeta(meta -> meta.displayName(MM.deserialize(miniName)));
        return item;
    }
}
