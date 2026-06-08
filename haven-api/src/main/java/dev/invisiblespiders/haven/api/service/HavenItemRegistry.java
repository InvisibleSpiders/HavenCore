package dev.invisiblespiders.haven.api.service;

import dev.invisiblespiders.haven.api.model.HavenItem;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Optional;

public interface HavenItemRegistry {

    /** Registers a custom item definition. Call from onEnable. */
    void register(HavenItem item);

    Optional<HavenItem> get(NamespacedKey key);

    /**
     * Identifies an ItemStack by inspecting its PersistentDataContainer.
     * Returns the registered HavenItem or empty for unregistered stacks.
     */
    Optional<HavenItem> identify(ItemStack stack);

    Collection<HavenItem> getAll();

    /** Builds an ItemStack from a HavenItem definition. */
    ItemStack build(HavenItem item, int amount);
}
