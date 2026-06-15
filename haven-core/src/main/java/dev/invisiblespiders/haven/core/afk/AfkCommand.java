package dev.invisiblespiders.haven.core.afk;

import dev.invisiblespiders.haven.api.model.HavenPlayer;
import dev.invisiblespiders.haven.api.service.HavenPlayerService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class AfkCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final AfkManager afkManager;
    private final HavenPlayerService playerService;

    public AfkCommand(AfkManager afkManager, HavenPlayerService playerService) {
        this.afkManager = afkManager;
        this.playerService = playerService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MM.deserialize("<red>Only players can use this command."));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("notifications")) {
            return toggleNotifications(player);
        }

        if (!player.hasPermission("haven.afk.manual")) {
            player.sendMessage(MM.deserialize("<red>You don't have permission to do that."));
            return true;
        }

        boolean nowAfk = !afkManager.isAfk(player.getUniqueId());
        afkManager.setAfk(player.getUniqueId(), nowAfk);
        return true;
    }

    private boolean toggleNotifications(Player player) {
        Optional<HavenPlayer> hp = playerService.getCached(player.getUniqueId());
        if (hp.isEmpty()) {
            player.sendMessage(MM.deserialize("<red>Profile not loaded yet. Try again in a moment."));
            return true;
        }
        HavenPlayer havenPlayer = hp.get();
        boolean muted = "true".equals(havenPlayer.getData("haven-core", "afk-broadcast-muted").orElse(""));
        if (muted) {
            havenPlayer.removeData("haven-core", "afk-broadcast-muted");
            player.sendMessage(MM.deserialize("<green>AFK broadcast notifications enabled."));
        } else {
            havenPlayer.setData("haven-core", "afk-broadcast-muted", "true");
            player.sendMessage(MM.deserialize("<gray>AFK broadcast notifications disabled."));
        }
        playerService.save(havenPlayer);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("notifications");
        return List.of();
    }
}
