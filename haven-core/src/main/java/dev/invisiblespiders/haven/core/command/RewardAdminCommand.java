package dev.invisiblespiders.haven.core.command;

import dev.invisiblespiders.haven.api.reward.HavenRewardService;
import dev.invisiblespiders.haven.api.reward.RewardRecord;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import dev.invisiblespiders.haven.core.text.CoreText;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class RewardAdminCommand {

    private final ConfigManager config;
    private final HavenRewardService rewards;
    private final Function<String, OfflinePlayer> playerResolver;

    public RewardAdminCommand(ConfigManager config, HavenRewardService rewards,
                              Function<String, OfflinePlayer> playerResolver) {
        this.config = Objects.requireNonNull(config, "config");
        this.rewards = Objects.requireNonNull(rewards, "rewards");
        this.playerResolver = Objects.requireNonNull(playerResolver, "playerResolver");
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("haven.admin.rewards")) {
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
            case "revoke" -> revoke(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("haven.admin.rewards")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("list", "check", "grant", "revoke"), args[0]);
        }
        return List.of();
    }

    private void list(CommandSender sender, OfflinePlayer target, UUID targetId) {
        String name = target.getName() == null ? targetId.toString() : target.getName();
        List<RewardRecord> pending = rewards.pending(targetId);
        sender.sendMessage(CoreText.deserialize("<gold>Pending rewards for <white>" + name
                + "<gray>: <yellow>" + pending.size()));
        for (RewardRecord reward : pending) {
            sender.sendMessage(CoreText.deserialize("<gray>#" + reward.id() + " <white>" + reward.displayText()));
        }
    }

    private void grant(CommandSender sender, UUID targetId, String[] args) {
        if (args.length < 5) {
            sendUsage(sender);
            return;
        }
        RewardRecord record = rewards.enqueue(targetId, args[2], args[3], args[4], Map.of(), (Instant) null);
        sender.sendMessage(CoreText.deserialize("<green>Reward queued: #" + record.id()));
    }

    private void revoke(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender);
            return;
        }
        long rewardId;
        try {
            rewardId = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(CoreText.deserialize("<red>Reward id must be a number."));
            return;
        }
        rewards.revoke(rewardId, "admin").ifPresentOrElse(
                reward -> sender.sendMessage(CoreText.deserialize("<green>Reward revoked.")),
                () -> sender.sendMessage(CoreText.deserialize("<red>Reward not found."))
        );
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(CoreText.deserialize(
                "<gray>Usage: /haven rewards <list|check|grant|revoke> <player> [provider] [type] [display]"));
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase();
        return values.stream().filter(value -> value.startsWith(lower)).toList();
    }
}
