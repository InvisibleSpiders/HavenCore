package dev.invisiblespiders.haven.core.economy;

import dev.invisiblespiders.haven.core.hook.VaultUnlockedHook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MoneyEconomyAdapterTest {

    @Test
    void unavailableWhenNoEconomyProvider() {
        VaultUnlockedHook hook = mock(VaultUnlockedHook.class);
        when(hook.getEconomy()).thenReturn(null);

        MoneyEconomyAdapter adapter = new MoneyEconomyAdapter(hook);

        assertFalse(adapter.isAvailable());
    }
}
