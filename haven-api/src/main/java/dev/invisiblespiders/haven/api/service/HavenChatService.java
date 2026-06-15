package dev.invisiblespiders.haven.api.service;

import dev.invisiblespiders.haven.api.chat.ChatChannel;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface HavenChatService {

    /** Returns the channel with the given id, or empty if not configured. */
    Optional<ChatChannel> getChannel(String id);

    /** Returns the player's active channel. Falls back to the default channel. */
    ChatChannel getPlayerChannel(UUID uuid);

    /** Sets the player's active channel by id. */
    void setPlayerChannel(UUID uuid, String channelId);

    /**
     * Sends a raw MiniMessage string through the given channel's routing logic.
     * Applies PAPI placeholders and MiniMessage formatting before delivery.
     */
    void sendToChannel(Player sender, ChatChannel channel, String rawMessage);

    /** Returns all configured channels. */
    Collection<ChatChannel> getChannels();
}
