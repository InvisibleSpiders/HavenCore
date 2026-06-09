package dev.invisiblespiders.haven.core.service;

import dev.invisiblespiders.haven.api.model.MessageChannel;
import dev.invisiblespiders.haven.api.service.HavenNotificationService;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class NotificationServiceImpl implements HavenNotificationService {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final ConfigManager config;

    public NotificationServiceImpl(ConfigManager config) {
        this.config = config;
    }

    @Override
    public void send(Player player, String key, Map<String, String> placeholders) {
        String template = config.getMessage(key);
        if (template == null || template.isBlank()) return;

        MessageChannel channel = channelFromKey(key);
        sendComponent(player, channel, applyPlaceholders(template, placeholders));
    }

    @Override
    public void broadcast(Collection<? extends Player> players, String key, Map<String, String> placeholders) {
        players.forEach(p -> send(p, key, placeholders));
    }

    @Override
    public void sendRaw(Player player, MessageChannel channel, String miniMessage) {
        if (miniMessage == null || miniMessage.isBlank()) return;
        sendComponent(player, channel, MM.deserialize(miniMessage));
    }

    @Override
    public void reload() {
        config.reload();
    }

    private Component applyPlaceholders(String template, Map<String, String> placeholders) {
        if (placeholders.isEmpty()) return MM.deserialize(template);
        List<TagResolver> resolvers = new ArrayList<>(placeholders.size());
        placeholders.forEach((k, v) -> resolvers.add(Placeholder.parsed(k, v)));
        return MM.deserialize(template, TagResolver.resolver(resolvers));
    }

    private void sendComponent(Player player, MessageChannel channel, Component component) {
        switch (channel) {
            case CHAT       -> player.sendMessage(component);
            case ACTION_BAR -> player.sendActionBar(component);
            case TITLE      -> player.showTitle(Title.title(component, Component.empty()));
            case SUBTITLE   -> player.showTitle(Title.title(Component.empty(), component));
            case BOSS_BAR   -> player.sendMessage(component);
            case SOUND      -> {}
            case TOAST      -> player.sendMessage(component);
        }
    }

    private MessageChannel channelFromKey(String key) {
        // Convention: key ending in .actionbar, .title, etc. selects channel
        if (key.endsWith(".actionbar")) return MessageChannel.ACTION_BAR;
        if (key.endsWith(".title"))     return MessageChannel.TITLE;
        if (key.endsWith(".subtitle"))  return MessageChannel.SUBTITLE;
        return MessageChannel.CHAT;
    }
}
