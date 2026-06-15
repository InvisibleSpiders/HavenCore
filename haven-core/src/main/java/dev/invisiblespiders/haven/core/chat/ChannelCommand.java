package dev.invisiblespiders.haven.core.chat;

import dev.invisiblespiders.haven.api.chat.ChatChannel;
import dev.invisiblespiders.haven.api.service.HavenChatService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class ChannelCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final HavenChatService chatService;

    public ChannelCommand(HavenChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MM.deserialize("<red>Only players can use this command."));
            return true;
        }
        if (args.length < 1) {
            ChatChannel current = chatService.getPlayerChannel(player.getUniqueId());
            player.sendMessage(MM.deserialize("<gray>Current channel: <white>" + current.displayName()));
            player.sendMessage(MM.deserialize("<gray>Usage: /channel <id>"));
            return true;
        }
        String id = args[0].toLowerCase();
        var channel = chatService.getChannel(id);
        if (channel.isEmpty()) {
            player.sendMessage(MM.deserialize("<red>Unknown channel '<white>" + id + "<red>'."));
            return true;
        }
        ChatChannel target = channel.get();
        if (target.permission() != null && !player.hasPermission(target.permission())) {
            player.sendMessage(MM.deserialize("<red>You don't have permission to use that channel."));
            return true;
        }
        chatService.setPlayerChannel(player.getUniqueId(), id);
        player.sendMessage(MM.deserialize("<green>Switched to <white>" + target.displayName() + "<green> channel."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            return chatService.getChannels().stream()
                    .filter(c -> c.permission() == null || player.hasPermission(c.permission()))
                    .map(ChatChannel::id)
                    .filter(id -> id.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
