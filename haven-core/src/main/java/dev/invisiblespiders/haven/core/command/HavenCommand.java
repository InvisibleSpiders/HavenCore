package dev.invisiblespiders.haven.core.command;

import dev.invisiblespiders.haven.api.hook.HavenHook;
import dev.invisiblespiders.haven.api.hook.HavenHookStatus;
import dev.invisiblespiders.haven.api.service.HavenCodexService;
import dev.invisiblespiders.haven.api.service.HavenDataSource;
import dev.invisiblespiders.haven.api.service.HavenEconomyService;
import dev.invisiblespiders.haven.api.service.HavenHookRegistry;
import dev.invisiblespiders.haven.api.service.HavenStorageService;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import dev.invisiblespiders.haven.core.config.OpToggleSettings;
import dev.invisiblespiders.haven.core.diagnostic.CoreDiagnostics;
import dev.invisiblespiders.haven.core.diagnostic.DiagnosticResult;
import dev.invisiblespiders.haven.core.diagnostic.DiagnosticSeverity;
import dev.invisiblespiders.haven.core.hook.VaultUnlockedHook;
import dev.invisiblespiders.haven.core.service.OpToggleService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicesManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class HavenCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final ConfigManager config;
    private final HavenHookRegistry hooks;
    private final ExecutorService asyncExecutor;
    private final OpToggleService opToggleService;

    public HavenCommand(Plugin plugin, ConfigManager config, HavenHookRegistry hooks) {
        this(plugin, config, hooks, null, null);
    }

    public HavenCommand(Plugin plugin, ConfigManager config, HavenHookRegistry hooks, ExecutorService asyncExecutor) {
        this(plugin, config, hooks, asyncExecutor, null);
    }

    public HavenCommand(Plugin plugin, ConfigManager config, HavenHookRegistry hooks,
                        ExecutorService asyncExecutor, OpToggleService opToggleService) {
        this.plugin = plugin;
        this.config = config;
        this.hooks = hooks;
        this.asyncExecutor = asyncExecutor;
        this.opToggleService = opToggleService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!requirePermission(sender, "haven.use")) {
                return true;
            }
            sendStatus(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "help"    -> {
                if (requirePermission(sender, "haven.use")) sendHelp(sender);
            }
            case "status"  -> {
                if (requirePermission(sender, "haven.use")) sendStatus(sender);
            }
            case "doctor"  -> {
                if (requirePermission(sender, "haven.admin.doctor")) sendDoctor(sender);
            }
            case "version" -> {
                if (requirePermission(sender, "haven.use")) sendVersion(sender);
            }
            case "reload"  -> {
                if (!sender.hasPermission("haven.admin.reload")) {
                    sender.sendMessage(MM.deserialize("<red>No permission."));
                    return true;
                }
                config.reload();
                if (opToggleService != null) {
                    opToggleService.reload(OpToggleSettings.from(config.getOpToggle()));
                }
                sender.sendMessage(MM.deserialize("<green>HavenCore configuration files reloaded."));
                sender.sendMessage(MM.deserialize(
                    "<yellow>Restart required for hooks, economy, database, and service wiring changes."
                ));
            }
            case "toggleop" -> toggleOp(sender);
            default -> sender.sendMessage(MM.deserialize(
                "<gray>Usage: /haven [help|status|doctor|version|reload|toggleop]"
            ));
        }
        return true;
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage(MM.deserialize("<red>No permission."));
        return false;
    }

    private void toggleOp(CommandSender sender) {
        if (!(sender instanceof Player player) || opToggleService == null) {
            sendToggleDenied(sender);
            return;
        }

        refreshOpToggleSettings();
        OpToggleService.ToggleResult result = opToggleService.toggle(player);
        if (!result.allowed() || result.newOpState().isEmpty()) {
            sendToggleDenied(sender);
            return;
        }

        String state = result.newOpState().get() ? "enabled" : "disabled";
        sender.sendMessage(MM.deserialize("<green>Operator mode " + state + "."));
    }

    private void refreshOpToggleSettings() {
        config.reloadOpToggle();
        if (config.getOpToggle() != null) {
            opToggleService.reload(OpToggleSettings.from(config.getOpToggle()));
        }
    }

    private void sendToggleDenied(CommandSender sender) {
        sender.sendMessage(MM.deserialize("<red>You are not allowed to use this command."));
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(MM.deserialize("<gold><bold>HavenCore</bold> <gray>v" + plugin.getPluginMeta().getVersion()));
        sender.sendMessage(MM.deserialize("<gray>Hooks:"));
        for (HavenHook hook : hooks.getAll()) {
            String color = hookStatusColor(hook.getStatus());
            sender.sendMessage(MM.deserialize("  " + color + hook.getId()
                + " <dark_gray>[" + hook.getStatus() + "]"
                + hookDetails(hook)));
        }

        ServicesManager services = plugin.getServer().getServicesManager();
        sendEconomyStatus(sender, services.load(HavenEconomyService.class));
        sendServiceStatus(sender, services);
    }

    private void sendDoctor(CommandSender sender) {
        List<DiagnosticResult> results = new CoreDiagnostics(
            plugin, config, hooks, asyncExecutor, opToggleService
        ).run();
        sender.sendMessage(MM.deserialize("<gold><bold>HavenCore Doctor</bold>"));
        for (DiagnosticResult result : results) {
            sender.sendMessage(MM.deserialize("  " + diagnosticColor(result.severity())
                + result.severity().name()
                + " <gray>" + result.id()
                + " <dark_gray>- <white>" + result.message()));
        }
        long pass = count(results, DiagnosticSeverity.PASS);
        long warn = count(results, DiagnosticSeverity.WARN);
        long fail = count(results, DiagnosticSeverity.FAIL);
        sender.sendMessage(MM.deserialize("<gray>Summary: <green>pass=" + pass
            + " <yellow>warn=" + warn
            + " <red>fail=" + fail));
    }

    private void sendEconomyStatus(CommandSender sender, HavenEconomyService economy) {
        sender.sendMessage(MM.deserialize("<gray>Economy:"));
        if (economy == null) {
            sender.sendMessage(MM.deserialize("  <red>unregistered <dark_gray>[UNAVAILABLE]"));
            return;
        }

        boolean moneyAvailable = economy.isMoneyAvailable();
        boolean itemAvailable = economy.isItemAvailable();
        boolean available = moneyAvailable || itemAvailable;
        sender.sendMessage(MM.deserialize("  " + statusColor(available)
            + "preferred=<white>" + economy.getPreferredAdapter()
            + " <gray>money=<white>" + readyLabel(moneyAvailable)
            + " <gray>item=<white>" + readyLabel(itemAvailable)));
    }

    private void sendServiceStatus(CommandSender sender, ServicesManager services) {
        sender.sendMessage(MM.deserialize("<gray>Services:"));
        sender.sendMessage(MM.deserialize(serviceLine("database", isDatabaseReady(services))));
        sender.sendMessage(MM.deserialize(serviceLine("async", isAsyncReady())));
        sender.sendMessage(MM.deserialize(serviceLine("storage", services.load(HavenStorageService.class) != null)));
        sender.sendMessage(MM.deserialize(serviceLine("codex", services.load(HavenCodexService.class) != null)));
        if (opToggleService != null) {
            String state = opToggleService.isEnabled() ? "ENABLED" : "DISABLED";
            sender.sendMessage(MM.deserialize("  <gray>op-toggle <dark_gray>[" + state + "]"
                + " <gray>entries=<white>" + opToggleService.entryCount()));
        }
    }

    private boolean isDatabaseReady(ServicesManager services) {
        HavenDataSource dataSource = services.load(HavenDataSource.class);
        if (dataSource == null) {
            return false;
        }
        try {
            return dataSource.getDataSource() != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean isAsyncReady() {
        return asyncExecutor != null && !asyncExecutor.isShutdown() && !asyncExecutor.isTerminated();
    }

    private String serviceLine(String service, boolean ready) {
        return "  " + statusColor(ready) + service + " <dark_gray>[" + readyLabel(ready) + "]";
    }

    private String hookDetails(HavenHook hook) {
        if (!(hook instanceof VaultUnlockedHook vaultUnlocked)) {
            return "";
        }
        return " <gray>plugin=<white>" + (vaultUnlocked.isPluginPresent() ? "DETECTED" : "MISSING")
            + " <gray>provider=<white>" + readyLabel(vaultUnlocked.hasEconomyProvider());
    }

    private String statusColor(boolean ready) {
        return ready ? "<green>" : "<red>";
    }

    private String statusLabel(boolean ready, String readyLabel) {
        return ready ? readyLabel : "UNAVAILABLE";
    }

    private String readyLabel(boolean ready) {
        return ready ? "READY" : "UNAVAILABLE";
    }

    private String hookStatusColor(HavenHookStatus status) {
        return switch (status) {
            case AVAILABLE -> "<green>";
            case DISABLED, MISSING_PLUGIN -> "<gray>";
            case API_ERROR, MISCONFIGURED -> "<yellow>";
        };
    }

    private String diagnosticColor(DiagnosticSeverity severity) {
        return switch (severity) {
            case PASS -> "<green>";
            case WARN -> "<yellow>";
            case FAIL -> "<red>";
        };
    }

    private long count(List<DiagnosticResult> results, DiagnosticSeverity severity) {
        return results.stream().filter(result -> result.severity() == severity).count();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MM.deserialize("<gold><bold>HavenCore Commands</bold>"));
        sender.sendMessage(MM.deserialize("<gray>/haven status <dark_gray>- <white>Show hooks, economy, and service health <gray>(haven.use)"));
        sender.sendMessage(MM.deserialize("<gray>/haven doctor <dark_gray>- <white>Run core diagnostics for config, database, hooks, and services <gray>(haven.admin.doctor)"));
        sender.sendMessage(MM.deserialize("<gray>/haven version <dark_gray>- <white>Show HavenCore, Paper, and Java versions <gray>(haven.use)"));
        sender.sendMessage(MM.deserialize("<gray>/haven reload <dark_gray>- <white>Reload configuration files <gray>(haven.admin.reload)"));
        sender.sendMessage(MM.deserialize("<gray>/haven toggleop <dark_gray>- <white>Toggle OP for configured UUID entries only"));
    }

    private void sendVersion(CommandSender sender) {
        sender.sendMessage(MM.deserialize(
            "<gold>HavenCore <white>" + plugin.getPluginMeta().getVersion()
            + " <gray>| Paper <white>" + org.bukkit.Bukkit.getVersion()
            + " <gray>| Java <white>" + System.getProperty("java.version")
        ));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            if (sender.hasPermission("haven.use")) {
                subcommands.addAll(List.of("help", "status", "version"));
            }
            if (sender.hasPermission("haven.admin.reload")) {
                subcommands.add("reload");
            }
            if (sender.hasPermission("haven.admin.doctor")) {
                subcommands.add("doctor");
            }
            if (sender instanceof Player) {
                subcommands.add("toggleop");
            }
            String prefix = args[0].toLowerCase();
            return subcommands.stream()
                .sorted()
                .filter(subcommand -> subcommand.startsWith(prefix))
                .toList();
        }
        return List.of();
    }
}
