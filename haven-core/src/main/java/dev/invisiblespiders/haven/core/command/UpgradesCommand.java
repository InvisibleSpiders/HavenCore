package dev.invisiblespiders.haven.core.command;

import dev.invisiblespiders.haven.api.upgrade.HavenUpgradeService;
import dev.invisiblespiders.haven.api.upgrade.UpgradeViewRequest;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import dev.invisiblespiders.haven.core.text.CoreText;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;

public final class UpgradesCommand implements CommandExecutor {

    private final ConfigManager config;
    private final HavenUpgradeService upgrades;

    public UpgradesCommand(ConfigManager config, HavenUpgradeService upgrades) {
        this.config = Objects.requireNonNull(config, "config");
        this.upgrades = Objects.requireNonNull(upgrades, "upgrades");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CoreText.deserialize("<red>This command is for players only."));
            return true;
        }
        if (!player.hasPermission("haven.upgrades")) {
            player.sendMessage(CoreText.message(config, "upgrades.no-permission", "<red>No permission."));
            return true;
        }
        upgrades.openDialog(player, UpgradeViewRequest.all());
        return true;
    }
}
