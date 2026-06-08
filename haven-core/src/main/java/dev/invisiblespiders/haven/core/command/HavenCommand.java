package dev.invisiblespiders.haven.core.command;

import dev.invisiblespiders.haven.api.hook.HavenHook;
import dev.invisiblespiders.haven.api.service.HavenHookRegistry;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class HavenCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final ConfigManager config;
    private final HavenHookRegistry hooks;

    public HavenCommand(Plugin plugin, ConfigManager config, HavenHookRegistry hooks) {
        this.plugin = plugin;
        this.config = config;
        this.hooks = hooks;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendStatus(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "status"  -> sendStatus(sender);
            case "version" -> sendVersion(sender);
            case "reload"  -> {
                if (!sender.hasPermission("haven.admin.reload")) {
                    sender.sendMessage(MM.deserialize("<red>No permission."));
                    return true;
                }
                config.reload();
                sender.sendMessage(MM.deserialize("<green>HavenCore configuration reloaded."));
            }
            default -> sender.sendMessage(MM.deserialize(
                "<gray>Usage: /haven [status|version|reload]"
            ));
        }
        return true;
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(MM.deserialize("<gold><bold>HavenCore</bold> <gray>v" + plugin.getPluginMeta().getVersion()));
        sender.sendMessage(MM.deserialize("<gray>Hooks:"));
        for (HavenHook hook : hooks.getAll()) {
            String color = hook.isAvailable() ? "<green>" : "<red>";
            sender.sendMessage(MM.deserialize("  " + color + hook.getId()
                + " <dark_gray>[" + (hook.isAvailable() ? "LOADED" : "UNAVAILABLE") + "]"));
        }
    }

    private void sendVersion(CommandSender sender) {
        sender.sendMessage(MM.deserialize(
            "<gold>HavenCore <white>" + plugin.getPluginMeta().getVersion()
            + " <gray>| Paper <white>" + org.bukkit.Bukkit.getVersion()
            + " <gray>| Java <white>" + System.getProperty("java.version")
        ));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("status", "version", "reload");
        return List.of();
    }
}
