package dev.invisiblespiders.haven.core.command;

import dev.invisiblespiders.haven.core.config.ConfigManager;
import dev.invisiblespiders.haven.core.dialog.RewardDialog;
import dev.invisiblespiders.haven.core.text.CoreText;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;

public final class RewardsCommand implements CommandExecutor {

    private final ConfigManager config;
    private final RewardDialog dialog;

    public RewardsCommand(ConfigManager config, RewardDialog dialog) {
        this.config = Objects.requireNonNull(config, "config");
        this.dialog = Objects.requireNonNull(dialog, "dialog");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CoreText.deserialize("<red>This command is for players only."));
            return true;
        }
        if (!player.hasPermission("haven.rewards")) {
            player.sendMessage(CoreText.message(config, "rewards.no-permission", "<red>No permission."));
            return true;
        }
        dialog.open(player);
        return true;
    }
}
