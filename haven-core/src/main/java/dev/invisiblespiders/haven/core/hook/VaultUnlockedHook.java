package dev.invisiblespiders.haven.core.hook;

import dev.invisiblespiders.haven.api.hook.HavenHook;
import net.milkbowl.vault2.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultUnlockedHook implements HavenHook {

    private Economy economy;

    @Override
    public String getId() { return "vaultunlocked"; }

    @Override
    public void onEnable() {
        RegisteredServiceProvider<Economy> rsp =
            Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) economy = rsp.getProvider();
    }

    @Override
    public boolean isAvailable() { return economy != null; }

    @Override
    public void onDisable() { economy = null; }

    public Economy getEconomy() { return economy; }
}
