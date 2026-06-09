package dev.invisiblespiders.haven.core.diagnostic;

import dev.invisiblespiders.haven.api.service.HavenCodexService;
import dev.invisiblespiders.haven.api.service.HavenDataSource;
import dev.invisiblespiders.haven.api.service.HavenEconomyService;
import dev.invisiblespiders.haven.api.service.HavenHookRegistry;
import dev.invisiblespiders.haven.api.service.HavenStorageService;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import dev.invisiblespiders.haven.core.service.OpToggleService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class CoreDiagnosticsTest {

    @Test
    void reportsFailedCoreServicesWhenMissing() {
        Plugin plugin = mock(Plugin.class, RETURNS_DEEP_STUBS);
        ConfigManager config = mock(ConfigManager.class);
        HavenHookRegistry hooks = mock(HavenHookRegistry.class);
        when(hooks.getAll()).thenReturn(List.of());

        CoreDiagnostics diagnostics = new CoreDiagnostics(plugin, config, hooks, null, null);

        List<DiagnosticResult> results = diagnostics.run();

        assertResult(results, DiagnosticSeverity.FAIL, "config");
        assertResult(results, DiagnosticSeverity.FAIL, "database");
        assertResult(results, DiagnosticSeverity.FAIL, "async");
        assertResult(results, DiagnosticSeverity.WARN, "hooks");
        assertResult(results, DiagnosticSeverity.FAIL, "economy");
        assertResult(results, DiagnosticSeverity.FAIL, "storage");
        assertResult(results, DiagnosticSeverity.FAIL, "codex");
        assertResult(results, DiagnosticSeverity.WARN, "op-toggle");
    }

    @Test
    void reportsPassingCoreServicesWhenAvailable() throws SQLException {
        Plugin plugin = mock(Plugin.class, RETURNS_DEEP_STUBS);
        ServicesManager services = plugin.getServer().getServicesManager();
        ConfigManager config = loadedConfig();
        HavenHookRegistry hooks = mock(HavenHookRegistry.class);
        when(hooks.getAll()).thenReturn(List.of(mock(dev.invisiblespiders.haven.api.hook.HavenHook.class)));
        HavenEconomyService economy = mock(HavenEconomyService.class);
        when(economy.isMoneyAvailable()).thenReturn(true);
        HavenDataSource dataSource = mock(HavenDataSource.class);
        DataSource jdbc = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getDataSource()).thenReturn(jdbc);
        when(jdbc.getConnection()).thenReturn(connection);
        when(services.load(HavenEconomyService.class)).thenReturn(economy);
        when(services.load(HavenDataSource.class)).thenReturn(dataSource);
        when(services.load(HavenStorageService.class)).thenReturn(mock(HavenStorageService.class));
        when(services.load(HavenCodexService.class)).thenReturn(mock(HavenCodexService.class));
        ExecutorService asyncExecutor = mock(ExecutorService.class);
        OpToggleService opToggleService = mock(OpToggleService.class);

        CoreDiagnostics diagnostics = new CoreDiagnostics(plugin, config, hooks, asyncExecutor, opToggleService);

        List<DiagnosticResult> results = diagnostics.run();

        assertTrue(results.stream().noneMatch(result -> result.severity() == DiagnosticSeverity.FAIL));
        assertResult(results, DiagnosticSeverity.PASS, "config");
        assertResult(results, DiagnosticSeverity.PASS, "database");
        assertResult(results, DiagnosticSeverity.PASS, "async");
        assertResult(results, DiagnosticSeverity.PASS, "economy");
        assertResult(results, DiagnosticSeverity.PASS, "storage");
        assertResult(results, DiagnosticSeverity.PASS, "codex");
        verify(connection).close();
    }

    @Test
    void reportsConfigWarningsWhenLoadedConfigHasInvalidValues() {
        Plugin plugin = mock(Plugin.class, RETURNS_DEEP_STUBS);
        ConfigManager config = loadedConfig();
        config.getDatabase().set("type", "postgres");
        HavenHookRegistry hooks = mock(HavenHookRegistry.class);
        when(hooks.getAll()).thenReturn(List.of());

        CoreDiagnostics diagnostics = new CoreDiagnostics(plugin, config, hooks, null, null);

        DiagnosticResult result = diagnostics.run().stream()
            .filter(candidate -> candidate.id().equals("config"))
            .findFirst()
            .orElseThrow();

        assertEquals(DiagnosticSeverity.WARN, result.severity());
        assertTrue(result.message().contains("database.yml type 'postgres'"));
    }

    private static ConfigManager loadedConfig() {
        ConfigManager config = mock(ConfigManager.class);
        when(config.getMain()).thenReturn(new YamlConfiguration());
        when(config.getDatabase()).thenReturn(new YamlConfiguration());
        when(config.getMessages()).thenReturn(new YamlConfiguration());
        when(config.getEconomy()).thenReturn(new YamlConfiguration());
        when(config.getStorage()).thenReturn(new YamlConfiguration());
        when(config.getCodex()).thenReturn(new YamlConfiguration());
        when(config.getHooks()).thenReturn(new YamlConfiguration());
        when(config.getOpToggle()).thenReturn(new YamlConfiguration());
        return config;
    }

    private static void assertResult(List<DiagnosticResult> results, DiagnosticSeverity severity, String id) {
        assertEquals(
            severity,
            results.stream()
                .filter(result -> result.id().equals(id))
                .findFirst()
                .orElseThrow()
                .severity()
        );
    }
}
