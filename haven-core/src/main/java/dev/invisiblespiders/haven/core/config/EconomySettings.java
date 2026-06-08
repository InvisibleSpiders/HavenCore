package dev.invisiblespiders.haven.core.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;

public record EconomySettings(
    Adapter preferredAdapter,
    boolean itemCurrencyEnabled,
    Material itemMaterial,
    String itemCurrencyTag,
    String itemDisplayName,
    List<String> itemLore
) {

    public enum Adapter {
        MONEY("money"),
        ITEM("item");

        private final String id;

        Adapter(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Adapter from(String value) {
            if (value != null && ITEM.id.equals(value.toLowerCase(Locale.ROOT))) {
                return ITEM;
            }
            return MONEY;
        }

        public static boolean isValid(String value) {
            if (value == null) {
                return false;
            }
            String normalized = value.toLowerCase(Locale.ROOT);
            return MONEY.id.equals(normalized) || ITEM.id.equals(normalized);
        }
    }

    public static EconomySettings from(FileConfiguration config) {
        String materialName = config.getString("item-currency.material", "EMERALD");
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.EMERALD;
        }

        return new EconomySettings(
            Adapter.from(config.getString("preferred-adapter", "money")),
            config.getBoolean("item-currency.enabled", false),
            material,
            config.getString("item-currency.pdc-tag", "haven:currency"),
            config.getString("item-currency.display-name", "<green>Haven Coin"),
            config.getStringList("item-currency.lore")
        );
    }

    public String preferredAdapterId() {
        return preferredAdapter.id();
    }
}
