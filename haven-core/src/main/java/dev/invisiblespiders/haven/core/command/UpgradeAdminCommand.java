package dev.invisiblespiders.haven.core.command;

import dev.invisiblespiders.haven.api.upgrade.HavenUpgradeService;
import dev.invisiblespiders.haven.api.upgrade.UpgradeDefinition;
import dev.invisiblespiders.haven.api.upgrade.UpgradePurchaseResult;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import dev.invisiblespiders.haven.core.text.CoreText;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class UpgradeAdminCommand {

    private final ConfigManager config;
    private final HavenUpgradeService upgrades;
    private final Function<String, OfflinePlayer> playerResolver;

    public UpgradeAdminCommand(ConfigManager config, HavenUpgradeService upgrades,
                               Function<String, OfflinePlayer> playerResolver) {
        this.config = Objects.requireNonNull(config, "config");
        this.upgrades = Objects.requireNonNull(upgrades, "upgrades");
        this.playerResolver = Objects.requireNonNull(playerResolver, "playerResolver");
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("haven.admin.upgrades")) {
            sender.sendMessage(CoreText.message(config, "haven.no-permission", "<red>No permission."));
            return true;
        }
        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String action = args[0].toLowerCase();
        OfflinePlayer target = playerResolver.apply(args[1]);
        UUID targetId = target.getUniqueId();
        switch (action) {
            case "list", "check" -> list(sender, target, targetId);
            case "grant" -> grant(sender, targetId, args);
            case "revoke" -> revoke(sender, targetId, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("haven.admin.upgrades")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("list", "check", "grant", "revoke"), args[0]);
        }
        return List.of();
    }

    private void list(CommandSender sender, OfflinePlayer target, UUID targetId) {
        String name = target.getName() == null ? targetId.toString() : target.getName();
        sender.sendMessage(CoreText.deserialize("<gold>Upgrades for <white>" + name));
        for (UpgradeDefinition definition : upgrades.definitions()) {
            int level = upgrades.currentLevel(targetId, definition.id());
            sender.sendMessage(CoreText.deserialize("<gray>" + definition.id() + ": <white>" + level));
        }
    }

    private void grant(CommandSender sender, UUID targetId, String[] args) {
        if (args.length < 4) {
            sendUsage(sender);
            return;
        }
        int level;
        try {
            level = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(CoreText.deserialize("<red>Level must be a number."));
            return;
        }
        UpgradePurchaseResult result = upgrades.grant(targetId, args[2], level, "admin");
        sender.sendMessage(CoreText.deserialize(result.succeeded() ? "<green>" + result.message()
                : "<red>" + result.message()));
    }

    private void revoke(CommandSender sender, UUID targetId, String[] args) {
        if (args.length < 3) {
            sendUsage(sender);
            return;
        }
        UpgradePurchaseResult result = upgrades.revoke(targetId, args[2], "admin");
        sender.sendMessage(CoreText.deserialize(result.succeeded() ? "<green>" + result.message()
                : "<red>" + result.message()));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(CoreText.deserialize(
                "<gray>Usage: /haven upgrades <list|check|grant|revoke> <player> [upgrade] [level]"));
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase();
        return values.stream().filter(value -> value.startsWith(lower)).toList();
    }
}
