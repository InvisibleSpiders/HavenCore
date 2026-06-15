package dev.invisiblespiders.haven.core.message;

import dev.invisiblespiders.haven.api.model.HavenPlayer;
import dev.invisiblespiders.haven.api.service.HavenMessageService;
import dev.invisiblespiders.haven.api.service.HavenPlayerService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

abstract class AbstractMsgCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final String type;
    private final HavenPlayerService playerService;
    private final HavenMessageService messageService;
    private final MiniMessageSanitizer sanitizer;
    private final Map<String, PresetDefinition> presets;

    AbstractMsgCommand(String type, Map<String, PresetDefinition> presets,
                        HavenPlayerService playerService, HavenMessageService messageService,
                        MiniMessageSanitizer sanitizer) {
        this.type = type;
        this.presets = presets;
        this.playerService = playerService;
        this.messageService = messageService;
        this.sanitizer = sanitizer;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MM.deserialize("<red>Only players can use this command."));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(MM.deserialize("<red>Usage: /" + label + " set|select|clear [value]"));
            return true;
        }
        return switch (args[0].toLowerCase()) {
            case "set" -> handleSet(player, label, args);
            case "select" -> handleSelect(player, args);
            case "clear" -> handleClear(player);
            default -> {
                player.sendMessage(MM.deserialize("<red>Usage: /" + label + " set|select|clear [value]"));
                yield true;
            }
        };
    }

    private boolean handleSet(Player player, String label, String[] args) {
        if (!player.hasPermission("haven.messages.custom")) {
            player.sendMessage(MM.deserialize("<red>You don't have permission to set a custom message."));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(MM.deserialize("<red>Usage: /" + label + " set <message>"));
            return true;
        }
        String raw = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String sanitized;
        try {
            sanitized = player.hasPermission("haven.messages.bypass-filter") ? raw : sanitizer.sanitize(raw);
        } catch (MiniMessageSanitizer.BlockedContentException e) {
            player.sendMessage(MM.deserialize("<red>" + e.getMessage()));
            return true;
        }
        Optional<HavenPlayer> hp = playerService.getCached(player.getUniqueId());
        if (hp.isEmpty()) {
            player.sendMessage(MM.deserialize("<red>Profile not loaded yet. Try again."));
            return true;
        }
        hp.get().setData("haven-core", type + "-msg-type", "custom");
        hp.get().setData("haven-core", type + "-msg-custom", sanitized);
        playerService.save(hp.get());
        player.sendMessage(MM.deserialize("<green>Custom " + type + " message set."));
        return true;
    }

    private boolean handleSelect(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MM.deserialize("<red>Usage: /" + type + "msg select <preset-id>"));
            return true;
        }
        String presetId = args[1];
        PresetDefinition preset = presets.get(presetId);
        if (preset == null) {
            player.sendMessage(MM.deserialize("<red>Unknown preset '<white>" + presetId + "<red>'."));
            return true;
        }
        boolean unlocked = preset.unlockType() == UnlockType.FREE
                || preset.isAvailableByPermission(player)
                || messageService.getUnlockedPresets(player.getUniqueId()).contains(presetId);
        if (!unlocked) {
            player.sendMessage(MM.deserialize("<red>You haven't unlocked that preset yet."));
            return true;
        }
        Optional<HavenPlayer> hp = playerService.getCached(player.getUniqueId());
        if (hp.isEmpty()) {
            player.sendMessage(MM.deserialize("<red>Profile not loaded yet. Try again."));
            return true;
        }
        hp.get().setData("haven-core", type + "-msg-type", "preset");
        hp.get().setData("haven-core", type + "-msg-preset", presetId);
        playerService.save(hp.get());
        player.sendMessage(MM.deserialize(
                "<green>Selected '<white>" + presetId + "<green>' as your " + type + " message."));
        return true;
    }

    private boolean handleClear(Player player) {
        Optional<HavenPlayer> hp = playerService.getCached(player.getUniqueId());
        if (hp.isEmpty()) {
            player.sendMessage(MM.deserialize("<red>Profile not loaded yet. Try again."));
            return true;
        }
        hp.get().removeData("haven-core", type + "-msg-type");
        hp.get().removeData("haven-core", type + "-msg-custom");
        hp.get().removeData("haven-core", type + "-msg-preset");
        playerService.save(hp.get());
        player.sendMessage(MM.deserialize("<green>Cleared your custom " + type + " message."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("set", "select", "clear");
        if (args.length == 2 && args[0].equalsIgnoreCase("select")) {
            return presets.keySet().stream()
                    .filter(id -> id.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
