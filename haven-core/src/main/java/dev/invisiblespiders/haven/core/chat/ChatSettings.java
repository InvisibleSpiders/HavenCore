package dev.invisiblespiders.haven.core.chat;

import dev.invisiblespiders.haven.api.chat.ChatChannel;
import dev.invisiblespiders.haven.api.chat.ChatRecipientType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public record ChatSettings(
        Map<String, ChatChannel> channels,
        ChatChannel defaultChannel,
        Map<String, ChatChannel> prefixChannels,
        MarketConfig market
) {
    public static ChatSettings from(FileConfiguration config) {
        Map<String, ChatChannel> channels = new LinkedHashMap<>();
        ChatChannel defaultChannel = null;
        Map<String, ChatChannel> prefixChannels = new HashMap<>();

        ConfigurationSection channelSection = config.getConfigurationSection("channels");
        if (channelSection != null) {
            for (String id : channelSection.getKeys(false)) {
                ConfigurationSection s = channelSection.getConfigurationSection(id);
                if (s == null) continue;
                ChatChannel channel = parseChannel(id, s);
                channels.put(id, channel);
                if (channel.isDefault()) defaultChannel = channel;
                if (channel.hasTriggerPrefix()) prefixChannels.put(channel.triggerPrefix(), channel);
            }
        }

        if (defaultChannel == null && !channels.isEmpty()) {
            defaultChannel = channels.values().iterator().next();
        }
        if (defaultChannel == null) {
            defaultChannel = new ChatChannel("global", "Global",
                    "<white><player_name>: <message>", ChatRecipientType.ALL,
                    null, 0, 0, null, true);
            channels.put("global", defaultChannel);
        }

        return new ChatSettings(channels, defaultChannel, prefixChannels, MarketConfig.from(config));
    }

    private static ChatChannel parseChannel(String id, ConfigurationSection s) {
        String typeName = s.getString("recipient-type", "ALL");
        ChatRecipientType type;
        try { type = ChatRecipientType.valueOf(typeName.toUpperCase()); }
        catch (IllegalArgumentException e) { type = ChatRecipientType.ALL; }

        return new ChatChannel(
                id,
                s.getString("display-name", id),
                s.getString("format", "<white><player_name>: <message>"),
                type,
                s.getString("permission"),
                s.getInt("radius-blocks", 0),
                s.getInt("cooldown-seconds", 0),
                s.getString("trigger-prefix"),
                s.getBoolean("default", false)
        );
    }

    public record MarketConfig(
            int slotInterval,
            int playerCooldown,
            String format,
            String warpCommand,
            String warpHover
    ) {
        public static MarketConfig from(FileConfiguration config) {
            return new MarketConfig(
                    config.getInt("market.slot-interval", 300),
                    config.getInt("market.player-cooldown", 600),
                    config.getString("market.format",
                            "<gold>[Market]</gold> <player_name> @ <shopwarp>: <message>"),
                    config.getString("market.warp-command", "/warp %s"),
                    config.getString("market.warp-hover", "<yellow>Click to visit %s!")
            );
        }
    }
}
