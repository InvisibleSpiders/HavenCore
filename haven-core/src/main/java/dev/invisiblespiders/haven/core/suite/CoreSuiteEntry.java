package dev.invisiblespiders.haven.core.suite;

import dev.invisiblespiders.haven.api.model.ReloadResult;
import dev.invisiblespiders.haven.api.model.SuiteHealthReport;
import dev.invisiblespiders.haven.api.model.SuiteHealthSeverity;
import dev.invisiblespiders.haven.api.service.HavenHookRegistry;
import dev.invisiblespiders.haven.api.service.HavenSuiteEntry;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import dev.invisiblespiders.haven.core.config.OpToggleSettings;
import dev.invisiblespiders.haven.core.diagnostic.CoreDiagnostics;
import dev.invisiblespiders.haven.core.diagnostic.DiagnosticSeverity;
import dev.invisiblespiders.haven.core.service.OpToggleService;
import org.bukkit.plugin.Plugin;
import java.util.List;
import java.util.concurrent.ExecutorService;

public final class CoreSuiteEntry implements HavenSuiteEntry {

    private final Plugin plugin;
    private final ConfigManager config;
    private final HavenHookRegistry hooks;
    private final ExecutorService asyncExecutor;
    private final OpToggleService opToggleService;

    public CoreSuiteEntry(
        Plugin plugin,
        ConfigManager config,
        HavenHookRegistry hooks,
        ExecutorService asyncExecutor,
        OpToggleService opToggleService
    ) {
        this.plugin = plugin;
        this.config = config;
        this.hooks = hooks;
        this.asyncExecutor = asyncExecutor;
        this.opToggleService = opToggleService;
    }

    @Override
    public String pluginName() { return "HavenCore"; }

    @Override
    public String pluginVersion() { return plugin.getPluginMeta().getVersion(); }

    @Override
    public List<SuiteHealthReport> health() {
        return new CoreDiagnostics(plugin, config, hooks, asyncExecutor, opToggleService)
            .run().stream()
            .map(d -> new SuiteHealthReport(mapSeverity(d.severity()), d.id(), d.message()))
            .toList();
    }

    @Override
    public ReloadResult reload() {
        try {
            config.reload();
            if (opToggleService != null) {
                opToggleService.reload(OpToggleSettings.from(config.getOpToggle()));
            }
            return ReloadResult.ok("HavenCore configuration reloaded.");
        } catch (Exception e) {
            return ReloadResult.fail("Reload failed: " + e.getMessage());
        }
    }

    private static SuiteHealthSeverity mapSeverity(DiagnosticSeverity s) {
        return switch (s) {
            case PASS -> SuiteHealthSeverity.PASS;
            case WARN -> SuiteHealthSeverity.WARN;
            case FAIL -> SuiteHealthSeverity.FAIL;
        };
    }
}
