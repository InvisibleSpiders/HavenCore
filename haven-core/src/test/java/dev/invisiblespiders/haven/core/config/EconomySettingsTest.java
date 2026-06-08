package dev.invisiblespiders.haven.core.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EconomySettingsTest {

    @Test
    void readsConfiguredEconomySettings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("preferred-adapter", "item");
        config.set("item-currency.enabled", true);
        config.set("item-currency.material", "DIAMOND");
        config.set("item-currency.pdc-tag", "haven:diamond");
        config.set("item-currency.display-name", "<aqua>Diamond Token");
        config.set("item-currency.lore", List.of("<gray>Shiny"));

        EconomySettings settings = EconomySettings.from(config);

        assertEquals(EconomySettings.Adapter.ITEM, settings.preferredAdapter());
        assertEquals("item", settings.preferredAdapterId());
        assertEquals(Material.DIAMOND, settings.itemMaterial());
        assertEquals("haven:diamond", settings.itemCurrencyTag());
        assertEquals("<aqua>Diamond Token", settings.itemDisplayName());
        assertEquals(List.of("<gray>Shiny"), settings.itemLore());
    }

    @Test
    void normalizesInvalidEconomySettingsToSafeDefaults() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("preferred-adapter", "barter");
        config.set("item-currency.enabled", false);
        config.set("item-currency.material", "NOT_A_MATERIAL");

        EconomySettings settings = EconomySettings.from(config);

        assertEquals(EconomySettings.Adapter.MONEY, settings.preferredAdapter());
        assertEquals(Material.EMERALD, settings.itemMaterial());
        assertFalse(settings.itemCurrencyEnabled());
    }
}
