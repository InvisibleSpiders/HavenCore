package dev.invisiblespiders.haven.core.service;

import dev.invisiblespiders.haven.api.model.HavenItem;
import dev.invisiblespiders.haven.api.service.HavenItemRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ItemRegistryImpl implements HavenItemRegistry {

    private static final String HAVEN_ITEM_KEY = "haven:item_key";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Map<String, HavenItem> registry = new ConcurrentHashMap<>();
    private final org.bukkit.plugin.Plugin plugin;

    public ItemRegistryImpl(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void register(HavenItem item) {
        registry.put(item.key().toString(), item);
    }

    @Override
    public Optional<HavenItem> get(NamespacedKey key) {
        return Optional.ofNullable(registry.get(key.toString()));
    }

    @Override
    public Optional<HavenItem> identify(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return Optional.empty();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return Optional.empty();
        NamespacedKey tag = new NamespacedKey(plugin, "item_key");
        String keyStr = meta.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
        if (keyStr == null) return Optional.empty();
        return Optional.ofNullable(registry.get(keyStr));
    }

    @Override
    public Collection<HavenItem> getAll() {
        return Collections.unmodifiableCollection(registry.values());
    }

    @Override
    public ItemStack build(HavenItem item, int amount) {
        ItemStack stack = new ItemStack(item.material(), amount);
        stack.editMeta(meta -> {
            if (!item.displayName().isBlank()) {
                meta.displayName(MM.deserialize(item.displayName()));
            }
            if (!item.lore().isEmpty()) {
                List<Component> loreComponents = item.lore().stream()
                    .map(MM::deserialize)
                    .toList();
                meta.lore(loreComponents);
            }
            if (item.customModelData() != 0) {
                meta.setCustomModelData(item.customModelData());
            }
            // Write the registry key into PDC for identification
            NamespacedKey tag = new NamespacedKey(plugin, "item_key");
            meta.getPersistentDataContainer().set(tag, PersistentDataType.STRING, item.key().toString());
            // Write extra PDC entries
            item.pdc().forEach((k, v) -> {
                NamespacedKey pdcKey = NamespacedKey.fromString(k, plugin);
                if (pdcKey != null) {
                    meta.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, v);
                }
            });
        });
        return stack;
    }
}
