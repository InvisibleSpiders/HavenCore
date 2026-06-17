package dev.invisiblespiders.haven.core.sleep;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SleepAdminCommand {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SleepManager sleepManager;
    private final Plugin plugin;

    public SleepAdminCommand(SleepManager sleepManager, Plugin plugin) {
        this.sleepManager = sleepManager;
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) { sendUsage(sender); return; }
        switch (args[0].toLowerCase()) {
            case "skip"   -> handleSkip(sender, args);
            case "toggle" -> handleToggle(sender, args);
            case "status" -> handleStatus(sender);
            default       -> sendUsage(sender);
        }
    }

    private void handleSkip(CommandSender sender, String[] args) {
        World world = resolveWorld(sender, args, 1);
        if (world == null) return;
        if (!sleepManager.getSettings().worlds().contains(world.getName())) {
            sender.sendMessage(MM.deserialize(
                "<red>'" + MM.escapeTags(world.getName()) + "' is not in the sleep module's world list."));
            return;
        }
        boolean triggered = sleepManager.forceSkip(world);
        if (triggered) {
            sender.sendMessage(MM.deserialize("<green>Force-triggered night skip in " + MM.escapeTags(world.getName()) + "."));
        } else {
            sender.sendMessage(MM.deserialize("<yellow>No night skip needed — " + MM.escapeTags(world.getName()) + " is already day or skip is in progress."));
        }
    }

    private void handleToggle(CommandSender sender, String[] args) {
        World world = resolveWorld(sender, args, 1);
        if (world == null) return;
        if (!sleepManager.getSettings().worlds().contains(world.getName())) {
            sender.sendMessage(MM.deserialize(
                "<red>'" + MM.escapeTags(world.getName()) + "' is not in the sleep module's world list."));
            return;
        }
        boolean newState = sleepManager.toggle(world);
        sender.sendMessage(MM.deserialize(
            "<green>Sleep module in " + MM.escapeTags(world.getName())
            + " is now " + (newState ? "<green>enabled" : "<red>disabled") + "<green>."));
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage(MM.deserialize("<gold><bold>Sleep Module Status</bold>"));
        for (String worldName : sleepManager.getSettings().worlds()) {
            World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                sender.sendMessage(MM.deserialize("  <red>" + MM.escapeTags(worldName) + " <dark_gray>[WORLD NOT LOADED]"));
                continue;
            }
            String state    = sleepManager.getState(world).name();
            int sleeping    = sleepManager.getSleepingCount(world);
            int active      = sleepManager.getActiveCount(world);
            boolean enabled = sleepManager.isEligible(world);
            sender.sendMessage(MM.deserialize(
                "  <gray>" + MM.escapeTags(worldName)
                + " <dark_gray>[" + state + "]"
                + (enabled ? "" : " <red>[DISABLED]")
                + " <gray>sleeping=<white>" + sleeping
                + " <gray>active=<white>" + active));
        }
    }

    private @Nullable World resolveWorld(CommandSender sender, String[] args, int argIndex) {
        String name;
        if (args.length > argIndex) {
            name = args[argIndex];
        } else if (sender instanceof Player player) {
            name = player.getWorld().getName();
        } else {
            sender.sendMessage(MM.deserialize(
                "<red>Specify a world: /haven sleep <command> <world>"));
            return null;
        }
        World world = plugin.getServer().getWorld(name);
        if (world == null) {
            sender.sendMessage(MM.deserialize("<red>World not found: " + MM.escapeTags(name)));
        }
        return world;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(MM.deserialize(
            "<gray>Usage: /haven sleep <skip|toggle|status> [world]"));
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return List.of("skip", "toggle", "status").stream()
                .filter(s -> s.startsWith(prefix))
                .toList();
        }
        if (args.length == 2
                && (args[0].equalsIgnoreCase("skip") || args[0].equalsIgnoreCase("toggle"))) {
            String prefix = args[1].toLowerCase();
            return plugin.getServer().getWorlds().stream()
                .map(World::getName)
                .filter(n -> n.toLowerCase().startsWith(prefix))
                .toList();
        }
        return List.of();
    }
}
