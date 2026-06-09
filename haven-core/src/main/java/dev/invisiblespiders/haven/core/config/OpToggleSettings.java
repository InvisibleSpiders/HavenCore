package dev.invisiblespiders.haven.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record OpToggleSettings(boolean enabled, List<Entry> entries) {

    private static final String CODE_PATTERN = "[A-Za-z0-9]{5}";

    public OpToggleSettings {
        entries = List.copyOf(entries);
    }

    public static OpToggleSettings from(FileConfiguration config) {
        List<Entry> entries = new ArrayList<>();
        Set<UUID> seenUuids = new HashSet<>();
        Set<String> seenCodes = new HashSet<>();
        Entry rootEntry = rootEntry(config);
        if (rootEntry != null) {
            addIfUnique(entries, rootEntry, seenUuids, seenCodes);
        }
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players != null) {
            for (String name : players.getKeys(false)) {
                UUID uuid = parseUuid(players.getString(name + ".uuid"));
                String code = players.getString(name + ".code", "");
                if (uuid != null && isValidCode(code)) {
                    addIfUnique(entries, new Entry(name, uuid, code), seenUuids, seenCodes);
                }
            }
        }
        return new OpToggleSettings(config.getBoolean("enabled", false), entries);
    }

    private static void addIfUnique(List<Entry> entries, Entry entry, Set<UUID> seenUuids, Set<String> seenCodes) {
        if (!seenUuids.add(entry.uuid())) {
            return;
        }
        if (!seenCodes.add(normalizeCode(entry.code()))) {
            seenUuids.remove(entry.uuid());
            return;
        }
        entries.add(entry);
    }

    private static Entry rootEntry(FileConfiguration config) {
        UUID uuid = parseUuid(config.getString("player", config.getString("uuid")));
        String code = config.getString("code", "");
        if (uuid == null || !isValidCode(code)) {
            return null;
        }
        return new Entry("player", uuid, code);
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

    static String normalizeCode(String code) {
        return code.toLowerCase(Locale.ROOT);
    }

    public record Entry(String name, UUID uuid, String code) {

        public String permission() {
            return "havencore.toggleop." + normalizeCode(code);
        }
    }
}
