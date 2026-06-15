package dev.invisiblespiders.haven.core.chat;

import dev.invisiblespiders.haven.api.service.HavenCooldownService;
import dev.invisiblespiders.haven.api.service.HavenWarpService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MarketCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String COOLDOWN_KEY = "market-advertise";

    private final MarketQueue queue;
    private final ChatSettings.MarketConfig config;
    private final HavenWarpService warpService;
    private final HavenCooldownService cooldownService;

    public MarketCommand(MarketQueue queue, ChatSettings.MarketConfig config,
                         HavenWarpService warpService, HavenCooldownService cooldownService) {
        this.queue = queue;
        this.config = config;
        this.warpService = warpService;
        this.cooldownService = cooldownService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MM.deserialize("<red>Only players can use this command."));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(MM.deserialize("<red>Usage: /market advertise <shopwarp> <message>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("mute")) {
            return handleMute(player);
        }

        if (!args[0].equalsIgnoreCase("advertise")) {
            player.sendMessage(MM.deserialize("<red>Usage: /market advertise <shopwarp> <message>"));
            return true;
        }

        if (!player.hasPermission("haven.market.advertise")) {
            player.sendMessage(MM.deserialize("<red>You don't have permission to do that."));
            return true;
        }

        if (args.length < 3) {
            player.sendMessage(MM.deserialize("<red>Usage: /market advertise <shopwarp> <message>"));
            return true;
        }

        String shopWarp = args[1];
        String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        if (!warpService.hasShopWarp(player.getUniqueId(), shopWarp)) {
            player.sendMessage(MM.deserialize("<red>You don't have a shop warp named '<white>"
                    + shopWarp + "<red>'. Create one with HavenWarps first."));
            return true;
        }

        // remaining() returns milliseconds; convert to seconds for the user-facing message
        long remainingMs = cooldownService.remaining(player.getUniqueId(), COOLDOWN_KEY);
        if (remainingMs > 0) {
            long remainingSeconds = (remainingMs + 999) / 1000;
            player.sendMessage(MM.deserialize("<red>You must wait <white>" + remainingSeconds
                    + "s<red> before advertising again."));
            return true;
        }

        // set() takes duration in milliseconds
        cooldownService.set(player.getUniqueId(), COOLDOWN_KEY, (long) config.playerCooldown() * 1000L);
        queue.enqueue(new AdvertisementRequest(
                player.getUniqueId(), player.getName(), shopWarp, message, System.currentTimeMillis()));
        player.sendMessage(MM.deserialize("<green>Advertisement queued! It will broadcast when your slot arrives."));
        return true;
    }

    private boolean handleMute(Player player) {
        if (!player.hasPermission("haven.market.mute")) {
            player.sendMessage(MM.deserialize("<red>You don't have permission to do that."));
            return true;
        }
        player.sendMessage(MM.deserialize("<yellow>Use <white>/market mute<yellow> to toggle market broadcasts."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("advertise", "mute");
        if (args.length == 2 && args[0].equalsIgnoreCase("advertise") && sender instanceof Player player) {
            return warpService.getShopWarps(player.getUniqueId()).stream()
                    .filter(w -> w.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
