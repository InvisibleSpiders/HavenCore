package dev.invisiblespiders.haven.core.message;

import dev.invisiblespiders.haven.api.model.HavenPlayer;
import dev.invisiblespiders.haven.api.service.HavenMessageService;
import dev.invisiblespiders.haven.api.service.HavenPlayerService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class PlayerMessageService implements HavenMessageService {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final MessageSettings settings;
    private final HavenPlayerService playerService;
    private final MiniMessageSanitizer sanitizer;

    public PlayerMessageService(MessageSettings settings, HavenPlayerService playerService,
                                 MiniMessageSanitizer sanitizer) {
        this.settings = settings;
        this.playerService = playerService;
        this.sanitizer = sanitizer;
    }

    @Override
    public Component getJoinMessage(Player player) {
        return resolve(player, "join", settings.joinPresets(),
                settings.defaultJoinPreset().message());
    }

    @Override
    public Component getQuitMessage(Player player) {
        return resolve(player, "quit", settings.quitPresets(),
                settings.defaultQuitPreset().message());
    }

    @Override
    public Component getAfkMessage(Player player) {
        return resolve(player, "afk", settings.afkPresets(),
                settings.defaultAfkPreset().message());
    }

    @Override
    public Component getDeathMessage(Player player, EntityDamageEvent.DamageCause cause,
                                      Entity killer) {
        var pool = settings.deathMessages().getOrDefault(cause,
                settings.deathMessages().getOrDefault(
                        EntityDamageEvent.DamageCause.CUSTOM, List.of()));
        List<DeathMessageEntry> eligible = pool.stream()
                .filter(e -> e.isUnrestricted() || player.hasPermission(e.permission()))
                .collect(Collectors.toList());
        if (eligible.isEmpty()) {
            eligible = List.of(new DeathMessageEntry("<player> died.", null));
        }
        DeathMessageEntry chosen = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        String killerName = killer != null ? killer.getName() : "unknown";
        return MM.deserialize(chosen.message(),
                Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("killer", killerName));
    }

    @Override
    public void unlockPreset(UUID uuid, String presetId) {
        playerService.getCached(uuid).ifPresent(hp -> {
            List<String> existing = getUnlockedPresetsFromPlayer(hp);
            if (!existing.contains(presetId)) {
                String updated = existing.isEmpty() ? presetId
                        : String.join(",", existing) + "," + presetId;
                hp.setData("haven-core", "unlocked-presets", updated);
                playerService.save(hp);
            }
        });
    }

    @Override
    public List<String> getUnlockedPresets(UUID uuid) {
        return playerService.getCached(uuid)
                .map(this::getUnlockedPresetsFromPlayer)
                .orElse(List.of());
    }

    private Component resolve(Player player, String type,
                               Map<String, PresetDefinition> presets, String fallback) {
        Optional<HavenPlayer> hp = playerService.getCached(player.getUniqueId());
        if (hp.isEmpty()) return deserializeTemplate(fallback, player);

        HavenPlayer havenPlayer = hp.get();
        String msgType = havenPlayer.getData("haven-core", type + "-msg-type").orElse(null);

        if ("custom".equals(msgType)) {
            String custom = havenPlayer.getData("haven-core", type + "-msg-custom").orElse(null);
            if (custom != null && !custom.isBlank()) return MM.deserialize(custom);
        }

        if ("preset".equals(msgType)) {
            String presetId = havenPlayer.getData("haven-core", type + "-msg-preset").orElse(null);
            PresetDefinition preset = presets.get(presetId);
            if (preset != null) return deserializeTemplate(preset.message(), player);
        }

        return deserializeTemplate(fallback, player);
    }

    private Component deserializeTemplate(String template, Player player) {
        return MM.deserialize(template, Placeholder.unparsed("player", player.getName()));
    }

    private List<String> getUnlockedPresetsFromPlayer(HavenPlayer hp) {
        String raw = hp.getData("haven-core", "unlocked-presets").orElse("");
        if (raw.isBlank()) return List.of();
        return Arrays.asList(raw.split(","));
    }
}
