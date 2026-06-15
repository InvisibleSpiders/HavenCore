package dev.invisiblespiders.haven.core.tab;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record TabSettings(
        int refreshInterval,
        List<String> header,
        List<String> footer,
        PlayerFormat playerFormat,
        String afkFormat
) {
    public static TabSettings from(FileConfiguration config) {
        return new TabSettings(
                config.getInt("refresh-interval", 5),
                config.getStringList("header"),
                config.getStringList("footer"),
                PlayerFormat.from(config),
                config.getString("afk-format", "<gray>[AFK] <player_name>")
        );
    }

    public record PlayerFormat(String defaultFormat, Map<String, String> groups) {
        public static PlayerFormat from(FileConfiguration config) {
            String def = config.getString("player-format.default", "<white><player_name>");
            Map<String, String> groups;
            var section = config.getConfigurationSection("player-format.groups");
            if (section == null) {
                groups = Map.of();
            } else {
                groups = section.getKeys(false).stream()
                        .collect(Collectors.toUnmodifiableMap(k -> k, k -> section.getString(k, def)));
            }
            return new PlayerFormat(def, groups);
        }

        public String resolveFormat(String group) {
            return groups.getOrDefault(group, defaultFormat);
        }
    }
}
