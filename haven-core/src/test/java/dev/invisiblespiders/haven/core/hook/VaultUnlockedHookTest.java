package dev.invisiblespiders.haven.core.hook;

import dev.invisiblespiders.haven.api.hook.HavenHookStatus;
import net.milkbowl.vault2.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VaultUnlockedHookTest {

    private static VaultUnlockedHook createHook() {
        return new VaultUnlockedHook(mock(Plugin.class), Logger.getLogger("test"));
    }

    @Test
    void notAvailableWhenNoProviderRegistered() {
        ServicesManager services = mock(ServicesManager.class);
        when(services.getRegistration(Economy.class)).thenReturn(null);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));

            VaultUnlockedHook hook = createHook();
            hook.onEnable();

            assertNull(hook.getEconomy());
            assertFalse(hook.isAvailable());
            assertEquals(HavenHookStatus.MISSING_PLUGIN, hook.getStatus());
        }
    }

    @Test
    void availableWhenProviderRegisteredBeforeEnable() {
        Economy economy = mock(Economy.class);
        RegisteredServiceProvider<Economy> provider = mock();
        when(provider.getProvider()).thenReturn(economy);

        ServicesManager services = mock(ServicesManager.class);
        when(services.getRegistration(Economy.class)).thenReturn(provider);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));

            VaultUnlockedHook hook = createHook();
            hook.onEnable();

            assertSame(economy, hook.getEconomy());
            assertTrue(hook.isAvailable());
            assertEquals(HavenHookStatus.AVAILABLE, hook.getStatus());
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
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));

            VaultUnlockedHook hook = createHook();
            hook.onEnable();
            assertTrue(hook.isAvailable());

            hook.onDisable();

            assertFalse(hook.isAvailable());
            assertNull(hook.getEconomy());
            assertEquals(HavenHookStatus.MISSING_PLUGIN, hook.getStatus());
        }
    }
}
