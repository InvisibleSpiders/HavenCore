package dev.invisiblespiders.haven.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

/**
 * Static service locator. Backed by Bukkit's ServicesManager.
 *
 * Usage from any Haven plugin:
 *   HavenCooldownService cs = HavenAPI.get(HavenCooldownService.class);
 */
public final class HavenAPI {

    private HavenAPI() {}

    /**
     * Returns the registered implementation for the given service.
     * Throws if HavenCore is not loaded or the service was not registered.
     */
    public static <T> T get(Class<T> service) {
        RegisteredServiceProvider<T> provider = Bukkit.getServicesManager().getRegistration(service);
        if (provider == null) {
            throw new IllegalStateException(
                "HavenAPI service not registered: " + service.getName()
                    + " — ensure HavenCore is loaded and enabled before accessing services."
            );
        }
        return provider.getProvider();
    }

    /**
     * Returns the service wrapped in Optional. Empty if not registered.
     */
    public static <T> Optional<T> optional(Class<T> service) {
        RegisteredServiceProvider<T> provider = Bukkit.getServicesManager().getRegistration(service);
        return Optional.ofNullable(provider).map(RegisteredServiceProvider::getProvider);
    }
}
