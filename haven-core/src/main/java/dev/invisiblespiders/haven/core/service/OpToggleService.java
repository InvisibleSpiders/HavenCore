package dev.invisiblespiders.haven.core.service;

import dev.invisiblespiders.haven.core.config.OpToggleSettings;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.Optional;

public class OpToggleService {

    private final Plugin plugin;
    private OpToggleSettings settings;

    public OpToggleService(Plugin plugin, OpToggleSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public void reload(OpToggleSettings settings) {
        this.settings = settings;
        registerPermissions();
    }

    public void registerPermissions() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        for (OpToggleSettings.Entry entry : settings.entries()) {
            if (pluginManager.getPermission(entry.permission()) == null) {
                pluginManager.addPermission(new Permission(
                    entry.permission(),
                    "Allows the configured UUID-bound HavenCore OP toggle entry.",
                    PermissionDefault.FALSE
                ));
            }
        }
    }

    public boolean isEnabled() {
        return settings.enabled();
    }

    public int entryCount() {
        return settings.entries().size();
    }

    public ToggleResult toggle(Player player) {
        if (!settings.enabled()) {
            return ToggleResult.denied();
        }

        Optional<OpToggleSettings.Entry> entry = settings.find(player.getUniqueId());
        if (entry.isEmpty() || !player.hasPermission(entry.get().permission())) {
            return ToggleResult.denied();
        }

        boolean previousOp = player.isOp();
        boolean newOp = !previousOp;
        player.setOp(newOp);
        plugin.getLogger().info("OP toggle used by " + player.getName()
            + " (" + player.getUniqueId() + "): " + previousOp + " -> " + newOp);
        return ToggleResult.allowed(newOp);
    }

    public record ToggleResult(boolean allowed, Optional<Boolean> newOpState) {

        public static ToggleResult allowed(boolean newOpState) {
            return new ToggleResult(true, Optional.of(newOpState));
        }

        public static ToggleResult denied() {
            return new ToggleResult(false, Optional.empty());
        }
    }
}
