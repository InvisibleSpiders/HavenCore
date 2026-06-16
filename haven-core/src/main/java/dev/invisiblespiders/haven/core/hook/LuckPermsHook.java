package dev.invisiblespiders.haven.core.hook;

import dev.invisiblespiders.haven.api.hook.HavenHook;
import dev.invisiblespiders.haven.api.hook.HavenHookStatus;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;

public class LuckPermsHook implements HavenHook {

    private LuckPerms api;

    @Override
    public String getId() { return "luckperms"; }

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            try {
                api = LuckPermsProvider.get();
            } catch (IllegalStateException ignored) {
                // LuckPerms present but provider not yet ready
            }
        }
    }

    @Override
    public boolean isAvailable() { return api != null; }

    @Override
    public HavenHookStatus getStatus() {
        if (api != null) return HavenHookStatus.AVAILABLE;
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) return HavenHookStatus.MISCONFIGURED;
        return HavenHookStatus.MISSING_PLUGIN;
    }

    @Override
    public void onDisable() { api = null; }

    public LuckPerms getApi() { return api; }
}
