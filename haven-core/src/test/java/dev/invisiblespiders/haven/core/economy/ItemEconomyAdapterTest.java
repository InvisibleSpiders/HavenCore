package dev.invisiblespiders.haven.core.economy;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemEconomyAdapterTest {

    @Test
    void formatterAppliesConfiguredNameLoreAndCurrencyTag() {
        Plugin plugin = mock(Plugin.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        when(meta.getPersistentDataContainer()).thenReturn(container);

        CurrencyItemFormatter formatter = new CurrencyItemFormatter(
            "haven:token",
            "<aqua>Diamond Token",
            List.of("<gray>Server currency")
        );

        formatter.apply(plugin, meta);

        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        var displayName = ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
        verify(meta).displayName(displayName.capture());
        assertEquals("Diamond Token", plain.serialize(displayName.getValue()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<net.kyori.adventure.text.Component>> lore = ArgumentCaptor.forClass(List.class);
        verify(meta).lore(lore.capture());
        assertEquals(List.of("Server currency"), lore.getValue().stream().map(plain::serialize).toList());

        NamespacedKey key = NamespacedKey.fromString("haven:token", plugin);
        assertNotNull(key);
        verify(container).set(eq(key), eq(PersistentDataType.BYTE), eq((byte) 1));
    }
}
