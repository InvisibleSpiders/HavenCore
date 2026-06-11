package dev.invisiblespiders.haven.core.hook;

import dev.invisiblespiders.haven.api.hook.HavenHookStatus;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExcellentEconomyHookTest {

    @Test
    void statusIsMissingWhenPluginAbsent() {
        ServicesManager services = mock(ServicesManager.class);
        when(services.getRegistration(ExcellentEconomyAPI.class)).thenReturn(null);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));

            ExcellentEconomyHook hook = new ExcellentEconomyHook();
            hook.onEnable();

            assertNull(hook.getApi());
            assertFalse(hook.hasApiProvider());
            assertFalse(hook.isAvailable());
            assertEquals(HavenHookStatus.MISSING_PLUGIN, hook.getStatus());
            verify(services, atLeastOnce()).getRegistration(ExcellentEconomyAPI.class);
        }
    }

    @Test
    void statusIsMisconfiguredWhenPluginPresentButNoProvider() {
        ServicesManager services = mock(ServicesManager.class);
        when(services.getRegistration(ExcellentEconomyAPI.class)).thenReturn(null);

        PluginManager plugins = mock(PluginManager.class);
        when(plugins.getPlugin("ExcellentEconomy")).thenReturn(mock(Plugin.class));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);

            ExcellentEconomyHook hook = new ExcellentEconomyHook();
            hook.onEnable();

            assertTrue(hook.isAvailable());
            assertFalse(hook.hasApiProvider());
            assertNull(hook.getApi());
            assertEquals(HavenHookStatus.MISCONFIGURED, hook.getStatus());
        }
    }

    @Test
    void statusIsAvailableWhenProviderRegistered() {
        ExcellentEconomyAPI api = mock(ExcellentEconomyAPI.class);
        RegisteredServiceProvider<ExcellentEconomyAPI> provider = mock();
        when(provider.getProvider()).thenReturn(api);

        ServicesManager services = mock(ServicesManager.class);
        when(services.getRegistration(ExcellentEconomyAPI.class)).thenReturn(provider);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));

            ExcellentEconomyHook hook = new ExcellentEconomyHook();
            hook.onEnable();

            assertSame(api, hook.getApi());
            assertTrue(hook.hasApiProvider());
            assertTrue(hook.isAvailable());
            assertEquals(HavenHookStatus.AVAILABLE, hook.getStatus());
        }
    }

    @Test
    void getApiCachesAfterFirstLookup() {
        ExcellentEconomyAPI api = mock(ExcellentEconomyAPI.class);
        RegisteredServiceProvider<ExcellentEconomyAPI> provider = mock();
        when(provider.getProvider()).thenReturn(api);

        ServicesManager services = mock(ServicesManager.class);
        when(services.getRegistration(ExcellentEconomyAPI.class)).thenReturn(provider);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));

            ExcellentEconomyHook hook = new ExcellentEconomyHook();
            hook.onEnable();
            hook.getApi();
            hook.getApi();
            hook.getApi();

            verify(services, times(1)).getRegistration(ExcellentEconomyAPI.class);
        }
    }

    @Test
    void onDisableClearsProvider() {
        ExcellentEconomyAPI api = mock(ExcellentEconomyAPI.class);
        RegisteredServiceProvider<ExcellentEconomyAPI> provider = mock();
        when(provider.getProvider()).thenReturn(api);

        ServicesManager services = mock(ServicesManager.class);
        when(services.getRegistration(ExcellentEconomyAPI.class)).thenReturn(provider);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));

            ExcellentEconomyHook hook = new ExcellentEconomyHook();
            hook.onEnable();
            hook.getApi();
            hook.onDisable();

            assertFalse(hook.isAvailable());
            assertFalse(hook.hasApiProvider());
            assertNull(hook.getApi());
            assertEquals(HavenHookStatus.MISSING_PLUGIN, hook.getStatus());
        }
    }
}
