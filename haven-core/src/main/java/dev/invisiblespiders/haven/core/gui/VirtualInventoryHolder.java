package dev.invisiblespiders.haven.core.gui;

import dev.invisiblespiders.haven.api.model.VirtualInventory;
import dev.invisiblespiders.haven.core.repository.VirtualInventoryRepository;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * Holder for virtual inventory GUIs.
 * On inventory close (handled by GuiListener), the contents are synced back to the model and saved.
 */
public class VirtualInventoryHolder implements InventoryHolder {

    private final VirtualInventory virtualInventory;
    private final VirtualInventoryRepository repo;
    private final Executor asyncExecutor;
    private final Logger logger;
    private Inventory inventory;

    public VirtualInventoryHolder(VirtualInventory virtualInventory,
                                  VirtualInventoryRepository repo,
                                  Executor asyncExecutor,
                                  Logger logger) {
        this.virtualInventory = virtualInventory;
        this.repo = repo;
        this.asyncExecutor = asyncExecutor;
        this.logger = logger;
    }

    /** Called by GuiListener on inventory close — syncs contents and saves. */
    public void onClose() {
        if (inventory == null) return;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            virtualInventory.setItem(i, contents[i]);
        }
        asyncExecutor.execute(() -> {
            try { repo.save(virtualInventory); }
            catch (SQLException e) { logger.warning("Failed to save virtual inventory on close: " + e.getMessage()); }
        });
    }

    @Override
    public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    public VirtualInventory getVirtualInventory() { return virtualInventory; }
}
