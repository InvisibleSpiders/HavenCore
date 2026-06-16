package dev.invisiblespiders.haven.core.sleep;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public record SleepSettings(
    List<String> worlds,
    int minCount,
    int minPercent,
    long minSpeed,
    long maxSpeed,
    SleepMessages messages
) {
    public SleepSettings {
        worlds = List.copyOf(worlds);
    }

    public static SleepSettings from(FileConfiguration config) {
        List<String> worlds = config.getStringList("worlds");
        if (worlds.isEmpty()) worlds = List.of("world");
        return new SleepSettings(
            worlds,
            config.getInt("threshold.min-count", 1),
            config.getInt("threshold.min-percent", 0),
            config.getLong("speed.min", 40L),
            config.getLong("speed.max", 200L),
            SleepMessages.from(config)
        );
    }

    public record SleepMessages(
        String sleeping,
        String skipStart,
        String skipPaused,
        String skipComplete,
        String broadcastSkip
    ) {
        public static SleepMessages from(FileConfiguration config) {
            return new SleepMessages(
                config.getString("messages.sleeping",      "<gray>☽ <white><sleeping></white>/<active> sleeping..."),
                config.getString("messages.skip-start",    "<gold>☽ Night is being skipped!"),
                config.getString("messages.skip-paused",   "<gray>☽ Skip paused — <white><sleeping></white>/<active> sleeping"),
                config.getString("messages.skip-complete", "<green>☀ Dawn breaks!"),
                config.getString("messages.broadcast-skip","<gold>☀ <gray>The night has passed.")
            );
        }
    }
}
