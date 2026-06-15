package dev.invisiblespiders.haven.core.chat;

import dev.invisiblespiders.haven.api.chat.ChatChannel;
import dev.invisiblespiders.haven.api.service.HavenChatService;
import dev.invisiblespiders.haven.core.hook.PlaceholderAPIHook;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ChatChannelService implements HavenChatService {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ChatSettings settings;
    private final PlaceholderAPIHook papiHook;
    private final Plugin plugin;
    private final Logger logger;

    private final ConcurrentHashMap<UUID, String> playerChannels = new ConcurrentHashMap<>();

    public ChatChannelService(ChatSettings settings, PlaceholderAPIHook papiHook, Plugin plugin) {
        this.settings = settings;
        this.papiHook = papiHook;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @Override
    public Optional<ChatChannel> getChannel(String id) {
        return Optional.ofNullable(settings.channels().get(id));
    }

    @Override
    public ChatChannel getPlayerChannel(UUID uuid) {
        String id = playerChannels.get(uuid);
        if (id == null) return settings.defaultChannel();
        return settings.channels().getOrDefault(id, settings.defaultChannel());
    }

    @Override
    public void setPlayerChannel(UUID uuid, String channelId) {
        if (settings.channels().containsKey(channelId)) {
            playerChannels.put(uuid, channelId);
        }
    }

    @Override
    public Collection<ChatChannel> getChannels() {
        return settings.channels().values();
    }

    @Override
    public void sendToChannel(Player sender, ChatChannel channel, String rawMessage) {
        Component formatted = buildMessage(sender, channel, rawMessage);
        switch (channel.recipientType()) {
            case ALL -> plugin.getServer().getOnlinePlayers().forEach(p -> p.sendMessage(formatted));
            case PERMISSION -> {
                String perm = channel.permission();
                if (perm == null) {
                    plugin.getServer().getOnlinePlayers().forEach(p -> p.sendMessage(formatted));
                } else {
                    plugin.getServer().getOnlinePlayers().stream()
                            .filter(p -> p.hasPermission(perm))
                            .forEach(p -> p.sendMessage(formatted));
                }
            }
            case RADIUS -> {
                int radius = channel.radiusBlocks();
                plugin.getServer().getOnlinePlayers().stream()
                        .filter(p -> p.getWorld().equals(sender.getWorld())
                                && p.getLocation().distanceSquared(sender.getLocation()) <= (long) radius * radius)
                        .forEach(p -> p.sendMessage(formatted));
            }
            case CLAIM -> {
                logger.warning("[Chat] CLAIM channel '" + channel.id()
                        + "' used but HavenClaimsService is not registered — falling back to ALL.");
                plugin.getServer().getOnlinePlayers().forEach(p -> p.sendMessage(formatted));
            }
        }
    }

    private Component buildMessage(Player sender, ChatChannel channel, String rawMessage) {
        String template = channel.format();
        if (papiHook.isAvailable()) {
            template = PlaceholderAPI.setPlaceholders(sender, template);
        }
        // Serialize rawMessage to plain text to prevent MiniMessage injection via chat input
        String safeMessage = PlainTextComponentSerializer.plainText().serialize(
                MM.deserialize(rawMessage));
        return MM.deserialize(template,
                Placeholder.unparsed("player_name", sender.getName()),
                Placeholder.unparsed("message", safeMessage));
    }

    /** Called by ChatChannelListener when a player disconnects — clears channel preference. */
    public void onQuit(UUID uuid) {
        playerChannels.remove(uuid);
    }
}
