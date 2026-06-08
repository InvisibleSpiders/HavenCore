package dev.invisiblespiders.haven.api.model;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;

import java.util.List;
import java.util.Map;

/**
 * Immutable definition of a custom Haven item.
 * Build an ItemStack via HavenItemRegistry.build(item, amount).
 */
public record HavenItem(
    NamespacedKey key,
    String displayName,         // MiniMessage
    List<String> lore,          // MiniMessage lines
    Material material,
    int customModelData,        // 0 = none
    Map<String, String> pdc    // extra PersistentDataContainer string entries
) {
    public HavenItem {
        lore = List.copyOf(lore);
        pdc = Map.copyOf(pdc);
    }

    public static Builder builder(NamespacedKey key, Material material) {
        return new Builder(key, material);
    }

    public static final class Builder {
        private final NamespacedKey key;
        private final Material material;
        private String displayName = "";
        private List<String> lore = List.of();
        private int customModelData = 0;
        private Map<String, String> pdc = Map.of();

        private Builder(NamespacedKey key, Material material) {
            this.key = key;
            this.material = material;
        }

        public Builder displayName(String name) { this.displayName = name; return this; }
        public Builder lore(List<String> lore) { this.lore = lore; return this; }
        public Builder customModelData(int cmd) { this.customModelData = cmd; return this; }
        public Builder pdc(Map<String, String> pdc) { this.pdc = pdc; return this; }

        public HavenItem build() {
            return new HavenItem(key, displayName, lore, material, customModelData, pdc);
        }
    }
}
