package dev.invisiblespiders.haven.core.diagnostic;

import dev.invisiblespiders.haven.api.hook.HavenHook;
import dev.invisiblespiders.haven.api.service.HavenCodexService;
import dev.invisiblespiders.haven.api.service.HavenDataSource;
import dev.invisiblespiders.haven.api.service.HavenEconomyService;
import dev.invisiblespiders.haven.api.service.HavenHookRegistry;
import dev.invisiblespiders.haven.api.service.HavenStorageService;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import dev.invisiblespiders.haven.core.service.OpToggleService;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicesManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class CoreDiagnostics {

    private final Plugin plugin;
    private final ConfigManager config;
    private final HavenHookRegistry hooks;
    private final ExecutorService asyncExecutor;
    private final OpToggleService opToggleService;

    public CoreDiagnostics(Plugin plugin, ConfigManager config, HavenHookRegistry hooks,
                           ExecutorService asyncExecutor, OpToggleService opToggleService) {
        this.plugin = plugin;
        this.config = config;
        this.hooks = hooks;
        this.asyncExecutor = asyncExecutor;
        this.opToggleService = opToggleService;
    }

    public List<DiagnosticResult> run() {
        ServicesManager services = plugin.getServer().getServicesManager();
        List<DiagnosticResult> results = new ArrayList<>();
        results.add(checkConfig());
        results.add(checkDatabase(services));
        results.add(checkAsync());
        results.add(checkHooks());
        results.add(checkEconomy(services));
        results.add(checkRegisteredService(services, HavenStorageService.class, "storage"));
        results.add(checkRegisteredService(services, HavenCodexService.class, "codex"));
        results.add(checkOpToggle());
        return results;
    }

    private DiagnosticResult checkConfig() {
        List<String> missing = new ArrayList<>();
        if (config.getMain() == null) missing.add("config.yml");
        if (config.getDatabase() == null) missing.add("database.yml");
        if (config.getMessages() == null) missing.add("messages.yml");
        if (config.getEconomy() == null) missing.add("economy.yml");
        if (config.getStorage() == null) missing.add("storage.yml");
        if (config.getCodex() == null) missing.add("codex.yml");
        if (config.getHooks() == null) missing.add("hooks.yml");
        if (config.getOpToggle() == null) missing.add("op-toggle.yml");
        if (!missing.isEmpty()) {
            return fail("config", "Missing loaded config sections: " + String.join(", ", missing));
        }
        return pass("config", "All HavenCore config files are loaded.");
    }

    private DiagnosticResult checkDatabase(ServicesManager services) {
        HavenDataSource service = services.load(HavenDataSource.class);
        if (service == null) {
            return fail("database", "HavenDataSource service is not registered.");
        }
        DataSource dataSource;
        try {
            dataSource = service.getDataSource();
        } catch (RuntimeException e) {
            return fail("database", "HavenDataSource threw while resolving the pool: " + e.getMessage());
        }
        if (dataSource == null) {
            return fail("database", "Database pool is not initialized.");
        }
        try (Connection ignored = dataSource.getConnection()) {
            return pass("database", "Database pool accepted a connection.");
        } catch (SQLException e) {
            return fail("database", "Database connection failed: " + e.getMessage());
        }
    }

    private DiagnosticResult checkAsync() {
        if (asyncExecutor == null) {
            return fail("async", "Async executor is not wired.");
        }
        if (asyncExecutor.isShutdown() || asyncExecutor.isTerminated()) {
            return fail("async", "Async executor is shut down.");
        }
        return pass("async", "Async executor is running.");
    }

    private DiagnosticResult checkHooks() {
        List<HavenHook> registered = new ArrayList<>(hooks.getAll());
        if (registered.isEmpty()) {
            return warn("hooks", "No optional hooks are registered.");
        }
        List<String> unavailableIds = registered.stream()
            .filter(hook -> !hook.isAvailable())
            .map(HavenHook::getId)
            .toList();
        if (!unavailableIds.isEmpty()) {
            return warn("hooks", unavailableIds.size() + " of " + registered.size()
                + " registered hooks are unavailable: " + String.join(", ", unavailableIds));
        }
        return pass("hooks", registered.size() + " hooks are registered and available.");
    }

    private DiagnosticResult checkEconomy(ServicesManager services) {
        HavenEconomyService economy = services.load(HavenEconomyService.class);
        if (economy == null) {
            return fail("economy", "HavenEconomyService is not registered.");
        }
        boolean money = economy.isMoneyAvailable();
        boolean item = economy.isItemAvailable();
        if (!money && !item) {
            return warn("economy", "Economy service is registered but no adapter is available.");
        }
        return pass("economy", "Economy service is available.");
    }

    private DiagnosticResult checkRegisteredService(ServicesManager services, Class<?> serviceType, String id) {
        if (services.load(serviceType) == null) {
            return fail(id, serviceType.getSimpleName() + " is not registered.");
        }
        return pass(id, serviceType.getSimpleName() + " is registered.");
    }

    private DiagnosticResult checkOpToggle() {
        if (opToggleService == null) {
            return warn("op-toggle", "OP toggle service is not wired.");
        }
        if (!opToggleService.isEnabled()) {
            return pass("op-toggle", "OP toggle is disabled.");
        }
        int entries = opToggleService.entryCount();
        if (entries == 0) {
            return fail("op-toggle", "OP toggle is enabled but has no valid UUID/code entries.");
        }
        return pass("op-toggle", "OP toggle is enabled with " + entries + " valid entries.");
    }

    private static DiagnosticResult pass(String id, String message) {
        return new DiagnosticResult(DiagnosticSeverity.PASS, id, message);
    }

    private static DiagnosticResult warn(String id, String message) {
        return new DiagnosticResult(DiagnosticSeverity.WARN, id, message);
    }

    private static DiagnosticResult fail(String id, String message) {
        return new DiagnosticResult(DiagnosticSeverity.FAIL, id, message);
    }
}
