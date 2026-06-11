package dev.invisiblespiders.haven.core.hook;

import dev.invisiblespiders.haven.api.hook.HavenHook;
import dev.invisiblespiders.haven.api.hook.HavenHookStatus;
import net.milkbowl.vault2.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultUnlockedHook implements HavenHook {

    private Economy economy;
    private boolean pluginPresent;
    private boolean initialized;

    @Override
    public String getId() { return "vaultunlocked"; }

    @Override
    public void onEnable() {
        pluginPresent = Bukkit.getPluginManager().getPlugin("VaultUnlocked") != null
            || Bukkit.getPluginManager().getPlugin("Vault") != null;
        initialized = true;
        // Economy provider lookup is deferred to first use — economy plugins
        // (e.g. ExcellentEconomy) register after HavenCore's onEnable.
    }

    @Override
    public boolean isAvailable() { return pluginPresent || economy != null; }

    @Override
    public HavenHookStatus getStatus() {
        if (economy != null) {
            return HavenHookStatus.AVAILABLE;
        }
        if (pluginPresent) {
            return HavenHookStatus.MISCONFIGURED;
        }
        return HavenHookStatus.MISSING_PLUGIN;
    }

    @Override
    public void onDisable() {
        economy = null;
        pluginPresent = false;
        initialized = false;
    }

    public Economy getEconomy() {
        if (economy == null && initialized) {
            RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
        return economy;
    }

    public boolean isPluginPresent() { return pluginPresent; }

    public boolean hasEconomyProvider() { return getEconomy() != null; }
}
