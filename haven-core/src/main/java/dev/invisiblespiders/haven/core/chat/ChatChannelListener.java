package dev.invisiblespiders.haven.core.chat;

import dev.invisiblespiders.haven.api.chat.ChatChannel;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class ChatChannelListener implements Listener {

    private final ChatChannelService service;
    private final ChatSettings settings;

    public ChatChannelListener(ChatChannelService service, ChatSettings settings) {
        this.service = service;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        event.setCancelled(true);
        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        ChatChannel channel = resolveChannel(event.getPlayer(), rawMessage);
        String messageContent = rawMessage;
        if (channel.hasTriggerPrefix() && rawMessage.startsWith(channel.triggerPrefix())) {
            messageContent = rawMessage.substring(channel.triggerPrefix().length()).stripLeading();
        }
        service.sendToChannel(event.getPlayer(), channel, messageContent);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.onQuit(event.getPlayer().getUniqueId());
    }

    private ChatChannel resolveChannel(org.bukkit.entity.Player player, String rawMessage) {
        for (var entry : settings.prefixChannels().entrySet()) {
            if (rawMessage.startsWith(entry.getKey())) {
                ChatChannel prefixChannel = entry.getValue();
                if (prefixChannel.permission() == null || player.hasPermission(prefixChannel.permission())) {
                    return prefixChannel;
                }
            }
        }
        return service.getPlayerChannel(player.getUniqueId());
    }
}
