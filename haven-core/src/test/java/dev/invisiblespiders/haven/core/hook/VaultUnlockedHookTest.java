package dev.invisiblespiders.haven.core.hook;

import net.milkbowl.vault2.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VaultUnlockedHookTest {

    @Test
    void onEnableStaysUnavailableWhenEconomyProviderMissing() {
        ServicesManager services = mock(ServicesManager.class);
        when(services.getRegistration(Economy.class)).thenReturn(null);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);

            VaultUnlockedHook hook = new VaultUnlockedHook();
            hook.onEnable();

            assertFalse(hook.isAvailable());
            assertNull(hook.getEconomy());
            verify(services).getRegistration(Economy.class);
        }
    }

    @Test
    void onEnableUsesRegisteredEconomyProvider() {
        Economy economy = mock(Economy.class);
        RegisteredServiceProvider<Economy> provider = mock();
        when(provider.getProvider()).thenReturn(economy);

        ServicesManager services = mock(ServicesManager.class);
        when(services.getRegistration(Economy.class)).thenReturn(provider);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);

            VaultUnlockedHook hook = new VaultUnlockedHook();
            hook.onEnable();

            assertTrue(hook.isAvailable());
            assertSame(economy, hook.getEconomy());
            verify(services).getRegistration(Economy.class);
        }
    }

    @Test
    void onDisableClearsEconomyProvider() {
        Economy economy = mock(Economy.class);
        RegisteredServiceProvider<Economy> provider = mock();
        when(provider.getProvider()).thenReturn(economy);

        ServicesManager services = mock(ServicesManager.class);
        when(services.getRegistration(Economy.class)).thenReturn(provider);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);

            VaultUnlockedHook hook = new VaultUnlockedHook();
            hook.onEnable();
            hook.onDisable();

            assertFalse(hook.isAvailable());
            assertNull(hook.getEconomy());
        }
    }
}
