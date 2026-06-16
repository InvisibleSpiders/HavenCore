package dev.invisiblespiders.haven.core.command;

import dev.invisiblespiders.haven.api.model.PlayerCodex;
import dev.invisiblespiders.haven.api.service.HavenCodexService;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import dev.invisiblespiders.haven.core.dialog.CodexDialog;
import dev.invisiblespiders.haven.core.text.CoreText;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

public final class CodexCommand implements CommandExecutor {

    private final ConfigManager config;
    private final HavenCodexService codexService;
    private final CodexDialog dialog;
    private final Plugin plugin;

    public CodexCommand(ConfigManager config, HavenCodexService codexService,
                        CodexDialog dialog, Plugin plugin) {
        this.config = Objects.requireNonNull(config, "config");
        this.codexService = Objects.requireNonNull(codexService, "codexService");
        this.dialog = Objects.requireNonNull(dialog, "dialog");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CoreText.deserialize("<red>This command is for players only."));
            return true;
        }
        if (!player.hasPermission("haven.codex")) {
            player.sendMessage(CoreText.message(config, "codex.no-permission", "<red>No permission."));
            return true;
        }
        codexService.getCodex(player.getUniqueId())
            .thenAccept((PlayerCodex playerCodex) ->
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    dialog.open(player, playerCodex)
                )
            )
            .exceptionally(ex -> {
                plugin.getLogger().warning("Failed to load codex for " + player.getName()
                    + ": " + ex.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage(CoreText.deserialize("<red>Failed to load your codex. Please try again."))
                );
                return null;
            });
        return true;
    }
}
