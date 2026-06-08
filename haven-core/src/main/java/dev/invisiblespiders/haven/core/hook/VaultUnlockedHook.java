package dev.invisiblespiders.haven.core.hook;

import dev.invisiblespiders.haven.api.hook.HavenHook;
import net.milkbowl.vault2.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultUnlockedHook implements HavenHook {

    private Economy economy;
    private boolean pluginPresent;

    @Override
    public String getId() { return "vaultunlocked"; }

    @Override
    public void onEnable() {
        pluginPresent = Bukkit.getPluginManager().getPlugin("VaultUnlocked") != null
            || Bukkit.getPluginManager().getPlugin("Vault") != null;
        RegisteredServiceProvider<Economy> rsp =
            Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) economy = rsp.getProvider();
    }

    @Override
    public boolean isAvailable() { return pluginPresent || economy != null; }

    @Override
    public void onDisable() {
        economy = null;
        pluginPresent = false;
    }

    public Economy getEconomy() { return economy; }

    public boolean hasEconomyProvider() { return economy != null; }
}
