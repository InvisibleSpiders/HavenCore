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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VaultUnlockedHookTest {

    @Test
    void onEnableStaysUnavailableWhenEconomyProviderMissing() {
        ServicesManager services = mock(ServicesManager.class);
        when(services.getRegistration(Economy.class)).thenReturn(null);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));

            VaultUnlockedHook hook = new VaultUnlockedHook();
            hook.onEnable();

            assertFalse(hook.isAvailable());
            assertEquals(HavenHookStatus.MISSING_PLUGIN, hook.getStatus());
            assertNull(hook.getEconomy());
            verify(services).getRegistration(Economy.class);
        }
    }

    @Test
    void onEnableIsAvailableWhenPluginIsInstalledWithoutEconomyProvider() {
        ServicesManager services = mock(ServicesManager.class);
        when(services.getRegistration(Economy.class)).thenReturn(null);

        PluginManager plugins = mock(PluginManager.class);
        when(plugins.getPlugin("VaultUnlocked")).thenReturn(mock(Plugin.class));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);

            VaultUnlockedHook hook = new VaultUnlockedHook();
            hook.onEnable();

            assertTrue(hook.isAvailable());
            assertEquals(HavenHookStatus.MISCONFIGURED, hook.getStatus());
            assertFalse(hook.hasEconomyProvider());
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
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));

            VaultUnlockedHook hook = new VaultUnlockedHook();
            hook.onEnable();

            assertTrue(hook.isAvailable());
            assertEquals(HavenHookStatus.AVAILABLE, hook.getStatus());
            assertTrue(hook.hasEconomyProvider());
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
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));

            VaultUnlockedHook hook = new VaultUnlockedHook();
            hook.onEnable();
            hook.onDisable();

            assertFalse(hook.isAvailable());
            assertEquals(HavenHookStatus.MISSING_PLUGIN, hook.getStatus());
            assertFalse(hook.hasEconomyProvider());
            assertNull(hook.getEconomy());
        }
    }
}
