package dev.invisiblespiders.haven.core.message;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record MessageSettings(
        FilterSettings filter,
        Map<String, PresetDefinition> joinPresets,
        Map<String, PresetDefinition> quitPresets,
        Map<String, PresetDefinition> afkPresets,
        Map<EntityDamageEvent.DamageCause, List<DeathMessageEntry>> deathMessages
) {
    public static MessageSettings from(FileConfiguration config) {
        return new MessageSettings(
                parseFilter(config),
                parsePresets(config, "join-presets", "<green>→ <player> joined."),
                parsePresets(config, "quit-presets", "<gray>← <player> left."),
                parsePresets(config, "afk-presets", "<gray><player> is now AFK."),
                parseDeathMessages(config)
        );
    }

    public PresetDefinition defaultJoinPreset() {
        return joinPresets.values().stream().filter(PresetDefinition::isDefault)
                .findFirst().orElse(joinPresets.values().iterator().next());
    }

    public PresetDefinition defaultQuitPreset() {
        return quitPresets.values().stream().filter(PresetDefinition::isDefault)
                .findFirst().orElse(quitPresets.values().iterator().next());
    }

    public PresetDefinition defaultAfkPreset() {
        return afkPresets.values().stream().filter(PresetDefinition::isDefault)
                .findFirst().orElse(afkPresets.values().iterator().next());
    }

    private static FilterSettings parseFilter(FileConfiguration config) {
        int maxLength = config.getInt("filter.max-length", 100);
        List<String> patterns = config.getStringList("filter.blocked-patterns");
        return new FilterSettings(maxLength, patterns);
    }

    private static Map<String, PresetDefinition> parsePresets(FileConfiguration config,
                                                               String section, String defaultMessage) {
        Map<String, PresetDefinition> presets = new LinkedHashMap<>();
        ConfigurationSection cs = config.getConfigurationSection(section);
        if (cs != null) {
            for (String id : cs.getKeys(false)) {
                ConfigurationSection s = cs.getConfigurationSection(id);
                if (s == null) continue;
                String typeName = s.getString("unlock-type", "FREE");
                UnlockType unlockType;
                try { unlockType = UnlockType.valueOf(typeName.toUpperCase()); }
                catch (IllegalArgumentException e) { unlockType = UnlockType.FREE; }
                presets.put(id, new PresetDefinition(
                        id,
                        s.getString("message", defaultMessage),
                        unlockType,
                        s.getString("codex-milestone"),
                        s.getString("permission"),
                        s.getBoolean("default", false)
                ));
            }
        }
        if (presets.isEmpty()) {
            presets.put("default", new PresetDefinition("default", defaultMessage,
                    UnlockType.FREE, null, null, true));
        }
        return presets;
    }

    private static Map<EntityDamageEvent.DamageCause, List<DeathMessageEntry>> parseDeathMessages(
            FileConfiguration config) {
        Map<EntityDamageEvent.DamageCause, List<DeathMessageEntry>> map =
                new EnumMap<>(EntityDamageEvent.DamageCause.class);
        ConfigurationSection section = config.getConfigurationSection("death-messages");
        if (section != null) {
            for (String causeName : section.getKeys(false)) {
                EntityDamageEvent.DamageCause cause;
                try { cause = EntityDamageEvent.DamageCause.valueOf(causeName.toUpperCase()); }
                catch (IllegalArgumentException e) { continue; }
                List<Map<?, ?>> entries = config.getMapList("death-messages." + causeName);
                List<DeathMessageEntry> messages = new ArrayList<>();
                for (Map<?, ?> entry : entries) {
                    Object msgObj = entry.get("message");
                    String msg = msgObj != null ? String.valueOf(msgObj) : causeName + " death.";
                    Object permObj = entry.get("permission");
                    String perm = permObj != null ? String.valueOf(permObj) : null;
                    messages.add(new DeathMessageEntry(msg, perm));
                }
                if (!messages.isEmpty()) map.put(cause, messages);
            }
        }
        map.computeIfAbsent(EntityDamageEvent.DamageCause.CUSTOM,
                k -> List.of(new DeathMessageEntry("<player> died.", null)));
        return map;
    }
}
