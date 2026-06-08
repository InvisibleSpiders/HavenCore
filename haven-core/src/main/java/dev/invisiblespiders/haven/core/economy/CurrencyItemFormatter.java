package dev.invisiblespiders.haven.core.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

final class CurrencyItemFormatter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final String pdcTag;
    private final String displayName;
    private final List<String> lore;

    CurrencyItemFormatter(String pdcTag, String displayName, List<String> lore) {
        this.pdcTag = pdcTag;
        this.displayName = displayName;
        this.lore = List.copyOf(lore);
    }

    void apply(Plugin plugin, ItemMeta meta) {
        if (!displayName.isBlank()) {
            meta.displayName(MM.deserialize(displayName));
        }
        if (!lore.isEmpty()) {
            List<Component> loreComponents = lore.stream()
                .map(MM::deserialize)
                .toList();
            meta.lore(loreComponents);
        }
        if (!pdcTag.isBlank()) {
            NamespacedKey key = NamespacedKey.fromString(pdcTag, plugin);
            if (key != null) {
                meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            }
        }
    }
}
