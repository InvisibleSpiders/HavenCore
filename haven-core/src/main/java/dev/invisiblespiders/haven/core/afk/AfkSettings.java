package dev.invisiblespiders.haven.core.afk;

import org.bukkit.configuration.file.FileConfiguration;

public record AfkSettings(
        int timeout,
        int kickTimeout,
        boolean strictMovement,
        ActivityEvents activityEvents,
        DetectionSettings detection,
        AfkMessages messages
) {
    public static AfkSettings from(FileConfiguration config) {
        return new AfkSettings(
                config.getInt("timeout", 300),
                config.getInt("kick-timeout", 1800),
                config.getBoolean("strict-movement", true),
                ActivityEvents.from(config),
                DetectionSettings.from(config),
                AfkMessages.from(config)
        );
    }

    public record ActivityEvents(boolean movement, boolean keyboardInput, boolean chat,
                                  boolean commands, boolean interact) {
        public static ActivityEvents from(FileConfiguration config) {
            return new ActivityEvents(
                    config.getBoolean("activity-events.movement", true),
                    config.getBoolean("activity-events.keyboard-input", true),
                    config.getBoolean("activity-events.chat", true),
                    config.getBoolean("activity-events.commands", true),
                    config.getBoolean("activity-events.interact", true)
            );
        }
    }

    public record DetectionSettings(float minRotationDelta, int patternMinIdleSeconds, boolean patternAlert,
                                     String patternAlertPermission) {
        public static DetectionSettings from(FileConfiguration config) {
            return new DetectionSettings(
                    (float) config.getDouble("detection.min-rotation-delta", 1.5),
                    config.getInt("detection.pattern-min-idle-seconds", 30),
                    config.getBoolean("detection.pattern-alert", true),
                    config.getString("detection.pattern-alert-permission", "haven.afk.alerts")
            );
        }
    }

    public record AfkMessages(String afkBroadcast, String returnBroadcast,
                               String actionBar, String kickReason) {
        public static AfkMessages from(FileConfiguration config) {
            return new AfkMessages(
                    config.getString("messages.afk-broadcast", "<gray><player> is now AFK."),
                    config.getString("messages.return-broadcast", "<gray><player> is no longer AFK."),
                    config.getString("messages.action-bar", "<yellow>You are AFK. Move to return."),
                    config.getString("messages.kick-reason", "You were kicked for being AFK.")
            );
        }
    }
}
