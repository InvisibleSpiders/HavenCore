package dev.invisiblespiders.haven.core.text;

import dev.invisiblespiders.haven.core.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;

public final class CoreText {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private CoreText() {
    }

    public static Component message(ConfigManager config, String key, String fallback) {
        return deserialize(messageString(config, key, fallback));
    }

    public static String messageString(ConfigManager config, String key, String fallback) {
        String message = config.getMessage(key);
        return blankToFallback(message, fallback);
    }

    public static Component config(FileConfiguration config, String key, String fallback) {
        return deserialize(configString(config, key, fallback));
    }

    public static String configString(FileConfiguration config, String key, String fallback) {
        if (config == null) {
            return fallback;
        }
        return blankToFallback(config.getString(key, fallback), fallback);
    }

    public static Component deserialize(String text) {
        return MM.deserialize(text);
    }

    private static String blankToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
