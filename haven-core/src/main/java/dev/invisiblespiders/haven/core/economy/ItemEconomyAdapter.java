package dev.invisiblespiders.haven.core.economy;

import dev.invisiblespiders.haven.api.model.EconomyAdapter;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;

public class ItemEconomyAdapter implements EconomyAdapter {

    private final Plugin plugin;
    private final Material material;
    private final String pdcTag;
    private final boolean enabled;
    private final CurrencyItemFormatter formatter;

    public ItemEconomyAdapter(Plugin plugin, Material material, String pdcTag, boolean enabled) {
        this(plugin, material, pdcTag, enabled, "", List.of());
    }

    public ItemEconomyAdapter(Plugin plugin, Material material, String pdcTag, boolean enabled,
                              String displayName, List<String> lore) {
        this.plugin = plugin;
        this.material = material;
        this.pdcTag = pdcTag;
        this.enabled = enabled;
        this.formatter = new CurrencyItemFormatter(pdcTag, displayName, lore);
    }

    @Override
    public boolean isAvailable() { return enabled; }

    @Override
    public double getBalance(UUID uuid) { return 0; } // item balance is per-player inventory, not uuid-based

    @Override
    public boolean has(UUID uuid, double amount) { return false; } // use hasItems(Player, int) instead

    @Override
    public boolean withdraw(UUID uuid, double amount) { return false; }

    @Override
    public boolean deposit(UUID uuid, double amount) { return false; }

    @Override
    public String format(double amount) { return (int) amount + "x " + material.name(); }

    public int countItems(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isCurrencyItem(stack)) count += stack.getAmount();
        }
        return count;
    }

    public boolean hasItems(Player player, int amount) {
        return countItems(player) >= amount;
    }

    public int removeItems(Player player, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (!isCurrencyItem(stack)) continue;
            int remove = Math.min(remaining, stack.getAmount());
            int newAmount = stack.getAmount() - remove;
            contents[i] = newAmount > 0 ? stack : null;
            if (newAmount > 0) stack.setAmount(newAmount);
            remaining -= remove;
        }
        player.getInventory().setContents(contents);
        return amount - remaining;
    }

    public void giveItems(Player player, int amount) {
        ItemStack currency = new ItemStack(material, amount);
        currency.editMeta(meta -> formatter.apply(plugin, meta));
        player.getInventory().addItem(currency);
    }

    private boolean isCurrencyItem(ItemStack stack) {
        if (stack == null || stack.getType() != material) return false;
        if (pdcTag.isBlank()) return true;
        var meta = stack.getItemMeta();
        if (meta == null) return false;
        NamespacedKey key = NamespacedKey.fromString(pdcTag, plugin);
        return key != null && meta.getPersistentDataContainer().has(key);
    }
}
