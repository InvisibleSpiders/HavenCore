package dev.invisiblespiders.haven.api.service;

import dev.invisiblespiders.haven.api.model.MessageChannel;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;

public interface HavenNotificationService {

    /**
     * Sends a message by template key. Placeholders are MiniMessage tags
     * merged with the provided map, e.g. {"player", "Steve"}.
     * No-op if the key is missing or the message is blank.
     */
    void send(Player player, String key, Map<String, String> placeholders);

    /** Convenience — no placeholders. */
    default void send(Player player, String key) {
        send(player, key, Map.of());
    }

    /** Sends to all players in the collection. */
    void broadcast(Collection<? extends Player> players, String key, Map<String, String> placeholders);

    /**
     * Sends a raw MiniMessage string to the player on the given channel.
     * Prefer keyed sends for configurable messages.
     */
    void sendRaw(Player player, MessageChannel channel, String miniMessage);

    /** Reloads templates from messages.yml. */
    void reload();
}
