package dev.invisiblespiders.haven.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public record OpToggleSettings(boolean enabled, List<Entry> entries) {

    private static final String CODE_PATTERN = "[A-Za-z0-9]{5}";

    public OpToggleSettings {
        entries = List.copyOf(entries);
    }

    public static OpToggleSettings from(FileConfiguration config) {
        List<Entry> entries = new ArrayList<>();
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players != null) {
            for (String name : players.getKeys(false)) {
                UUID uuid = parseUuid(players.getString(name + ".uuid"));
                String code = players.getString(name + ".code", "");
                if (uuid != null && isValidCode(code)) {
                    entries.add(new Entry(name, uuid, code));
                }
            }
        }
        return new OpToggleSettings(config.getBoolean("enabled", false), entries);
    }

    public Optional<Entry> find(UUID uuid) {
        return entries.stream()
            .filter(entry -> entry.uuid().equals(uuid))
            .findFirst();
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isValidCode(String value) {
        return value != null && value.matches(CODE_PATTERN);
    }

    public record Entry(String name, UUID uuid, String code) {

        public String permission() {
            return "havencore.toggleop." + code.toLowerCase(Locale.ROOT);
        }
    }
}
