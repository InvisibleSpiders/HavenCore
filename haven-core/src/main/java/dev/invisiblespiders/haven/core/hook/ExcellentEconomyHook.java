package dev.invisiblespiders.haven.core.hook;

import dev.invisiblespiders.haven.api.hook.HavenHook;
import dev.invisiblespiders.haven.api.hook.HavenHookStatus;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI;

public class ExcellentEconomyHook implements HavenHook {

    private ExcellentEconomyAPI api;
    private boolean pluginPresent;
    private boolean initialized;

    @Override
    public String getId() { return "excellenteconomy"; }

    @Override
    public void onEnable() {
        pluginPresent = Bukkit.getPluginManager().getPlugin("ExcellentEconomy") != null;
        initialized = true;
    }

    @Override
    public boolean isAvailable() { return pluginPresent || api != null; }

    @Override
    public HavenHookStatus getStatus() {
        if (hasApiProvider()) return HavenHookStatus.AVAILABLE;
        if (pluginPresent) return HavenHookStatus.MISCONFIGURED;
        return HavenHookStatus.MISSING_PLUGIN;
    }

    @Override
    public void onDisable() {
        api = null;
        pluginPresent = false;
        initialized = false;
    }

    public ExcellentEconomyAPI getApi() {
        if (api == null && initialized) {
            RegisteredServiceProvider<ExcellentEconomyAPI> rsp =
                Bukkit.getServicesManager().getRegistration(ExcellentEconomyAPI.class);
            if (rsp != null) api = rsp.getProvider();
        }
        return api;
    }

    public boolean hasApiProvider() { return getApi() != null; }
}
