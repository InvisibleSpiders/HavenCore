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
    void getEconomyReturnsNullWhenNoProviderRegistered() {
        ServicesManager services = mock(ServicesManager.class);
        when(services.getRegistration(Economy.class)).thenReturn(null);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));

            VaultUnlockedHook hook = new VaultUnlockedHook();
            hook.onEnable();

            assertNull(hook.getEconomy());
            assertFalse(hook.hasEconomyProvider());
            assertFalse(hook.isAvailable());
            assertEquals(HavenHookStatus.MISSING_PLUGIN, hook.getStatus());
            // Re-queries on every call when no provider is found — no exact count asserted.
            verify(services, atLeastOnce()).getRegistration(Economy.class);
        }
    }

    @Test
    void statusIsMisconfiguredWhenPluginPresentButNoProvider() {
        ServicesManager services = mock(ServicesManager.class);
        when(services.getRegistration(Economy.class)).thenReturn(null);

        PluginManager plugins = mock(PluginManager.class);
        when(plugins.getPlugin("VaultUnlocked")).thenReturn(mock(Plugin.class));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);

            VaultUnlockedHook hook = new VaultUnlockedHook();
            hook.onEnable();

            // isAvailable() reflects pluginPresent — true before provider registers.
            assertTrue(hook.isAvailable());
            // hasEconomyProvider() does the lazy lookup — still no provider in this test.
            assertFalse(hook.hasEconomyProvider());
            assertNull(hook.getEconomy());
            // getStatus() is MISCONFIGURED because provider query returned null.
            assertEquals(HavenHookStatus.MISCONFIGURED, hook.getStatus());
        }
    }

    @Test
    void getEconomyFindsProviderRegisteredBeforeFirstCall() {
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

            assertSame(economy, hook.getEconomy());
            assertTrue(hook.hasEconomyProvider());
            assertTrue(hook.isAvailable());
            assertEquals(HavenHookStatus.AVAILABLE, hook.getStatus());
        }
    }

    @Test
    void getEconomyFindsProviderRegisteredAfterEnable() {
        // Simulates ExcellentEconomy registering after HavenCore's onEnable.
        Economy economy = mock(Economy.class);
        RegisteredServiceProvider<Economy> provider = mock();
        when(provider.getProvider()).thenReturn(economy);

        ServicesManager services = mock(ServicesManager.class);
        // Provider absent during onEnable, present by the time getEconomy() is called.
        when(services.getRegistration(Economy.class)).thenReturn(provider);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));

            VaultUnlockedHook hook = new VaultUnlockedHook();
            hook.onEnable();
            // Economy plugin registers here in the real server; getEconomy() discovers it.
            assertSame(economy, hook.getEconomy());
            assertTrue(hook.hasEconomyProvider());
        }
    }

    @Test
    void getEconomyCachesProviderAfterFirstLookup() {
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
            hook.getEconomy();
            hook.getEconomy();
            hook.getEconomy();

            // getRegistration should only be called once — subsequent calls use the cache.
            verify(services, times(1)).getRegistration(Economy.class);
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
            hook.getEconomy(); // populate cache
            hook.onDisable();

            assertFalse(hook.isAvailable());
            assertFalse(hook.hasEconomyProvider());
            assertNull(hook.getEconomy());
            assertEquals(HavenHookStatus.MISSING_PLUGIN, hook.getStatus());
        }
    }
}
