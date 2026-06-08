package dev.invisiblespiders.haven.core.hook;

import dev.invisiblespiders.haven.api.hook.HavenHook;
import org.bukkit.Bukkit;

public class PlaceholderAPIHook implements HavenHook {

    private boolean available = false;

    @Override
    public String getId() { return "placeholderapi"; }

    @Override
    public void onEnable() {
        available = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    @Override
    public boolean isAvailable() { return available; }

    @Override
    public void onDisable() { available = false; }
}
