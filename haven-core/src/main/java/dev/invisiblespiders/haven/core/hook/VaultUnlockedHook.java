package dev.invisiblespiders.haven.core.hook;

import dev.invisiblespiders.haven.api.hook.HavenHook;
import net.milkbowl.vault2.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Logger;

/**
 * Hooks into VaultUnlocked's Economy service.
 *
 * <p>Vault and Vault-style abstractions register their Economy provider
 * dynamically — the provider may be available now, register later when an
 * economy plugin loads, or never appear if no provider plugin is installed.
 * This hook listens to ServiceRegisterEvent so it works regardless of load order.
 */
public class VaultUnlockedHook implements HavenHook, Listener {

    private final Plugin owner;
    private final Logger logger;
    private volatile Economy economy;

    public VaultUnlockedHook(Plugin owner, Logger logger) {
        this.owner = owner;
        this.logger = logger;
    }

    @Override
    public String getId() { return "vaultunlocked"; }

    @Override
    public void onEnable() {
        attemptLookup();
        Bukkit.getPluginManager().registerEvents(this, owner);
    }

    @Override
    public boolean isAvailable() { return economy != null; }

    @Override
    public void onDisable() {
        economy = null;
    }

    public Economy getEconomy() { return economy; }

    /** True if the Vault plugin itself is installed (even if no Economy provider yet). */
    public boolean isVaultPluginPresent() {
        return Bukkit.getPluginManager().getPlugin("Vault") != null;
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent event) {
        if (economy != null) return;
        if (event.getProvider().getService() == Economy.class) {
            attemptLookup();
            if (economy != null) {
                logger.info("VaultUnlocked Economy provider connected: "
                    + event.getProvider().getPlugin().getName());
            }
        }
    }

    @EventHandler
    public void onServiceUnregister(ServiceUnregisterEvent event) {
        if (event.getProvider().getService() == Economy.class) {
            attemptLookup();
            if (economy == null) {
                logger.info("VaultUnlocked Economy provider disconnected.");
            }
        }
    }

    private void attemptLookup() {
        try {
            RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
            economy = (rsp == null) ? null : rsp.getProvider();
        } catch (NoClassDefFoundError e) {
            economy = null;
        }
    }
}
