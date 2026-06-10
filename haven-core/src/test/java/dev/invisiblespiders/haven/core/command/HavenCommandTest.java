package dev.invisiblespiders.haven.core.command;

import dev.invisiblespiders.haven.api.service.HavenHookRegistry;
import dev.invisiblespiders.haven.api.hook.HavenHookStatus;
import dev.invisiblespiders.haven.api.service.HavenCodexService;
import dev.invisiblespiders.haven.api.service.HavenDataSource;
import dev.invisiblespiders.haven.api.service.HavenEconomyService;
import dev.invisiblespiders.haven.api.service.HavenStorageService;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import dev.invisiblespiders.haven.core.config.OpToggleSettings;
import dev.invisiblespiders.haven.core.hook.VaultUnlockedHook;
import dev.invisiblespiders.haven.core.service.OpToggleService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import io.papermc.paper.plugin.configuration.PluginMeta;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class HavenCommandTest {

    @Test
    void reloadExplainsThatOnlyConfigurationFilesWereReloaded() {
        Plugin plugin = mock(Plugin.class);
        ConfigManager config = mock(ConfigManager.class);
        HavenHookRegistry hooks = mock(HavenHookRegistry.class);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("haven.admin.reload")).thenReturn(true);

        HavenCommand command = new HavenCommand(plugin, config, hooks);

        command.onCommand(sender, mock(Command.class), "haven", new String[] {"reload"});

        verify(config).reload();
        ArgumentCaptor<Component> messageCaptor = ArgumentCaptor.forClass(Component.class);
        verify(sender, times(2)).sendMessage(messageCaptor.capture());
        List<String> messages = messageCaptor.getAllValues().stream()
            .map(component -> PlainTextComponentSerializer.plainText().serialize(component))
            .toList();

        assertTrue(messages.get(0).contains("configuration files reloaded"));
        assertTrue(messages.get(1).contains("Restart required"));
        assertTrue(messages.get(1).contains("hooks"));
        assertTrue(messages.get(1).contains("economy"));
        assertTrue(messages.get(1).contains("database"));
    }

    @Test
    void permissionFailuresUseConfiguredNoPermissionMessage() {
        Plugin plugin = mockPluginWithServices();
        ConfigManager config = mock(ConfigManager.class);
        when(config.getMessage("haven.no-permission")).thenReturn("<red>Custom denied.");
        HavenHookRegistry hooks = mock(HavenHookRegistry.class);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("haven.use")).thenReturn(false);

        HavenCommand command = new HavenCommand(plugin, config, hooks);

        command.onCommand(sender, mock(Command.class), "haven", new String[] {"status"});

        List<String> messages = sentPlainMessages(sender);
        assertTrue(messages.stream().anyMatch(message -> message.contains("Custom denied.")));
    }

    @Test
    void permissionFailuresUseFallbackWhenConfiguredMessageIsBlank() {
        Plugin plugin = mockPluginWithServices();
        ConfigManager config = mock(ConfigManager.class);
        when(config.getMessage("haven.no-permission")).thenReturn("");
        HavenHookRegistry hooks = mock(HavenHookRegistry.class);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("haven.use")).thenReturn(false);

        HavenCommand command = new HavenCommand(plugin, config, hooks);

        command.onCommand(sender, mock(Command.class), "haven", new String[] {"status"});

        List<String> messages = sentPlainMessages(sender);
        assertTrue(messages.stream().anyMatch(message -> message.contains("No permission.")));
    }

    @Test
    void statusShowsHookEconomyAndServiceHealth() {
        Plugin plugin = mockPluginWithServices();
        ConfigManager config = mock(ConfigManager.class);
        HavenHookRegistry hooks = mock(HavenHookRegistry.class);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("haven.use")).thenReturn(true);
        VaultUnlockedHook vaultUnlocked = mock(VaultUnlockedHook.class);
        when(vaultUnlocked.getId()).thenReturn("vaultunlocked");
        when(vaultUnlocked.isAvailable()).thenReturn(true);
        when(vaultUnlocked.getStatus()).thenReturn(HavenHookStatus.MISCONFIGURED);
        when(vaultUnlocked.isPluginPresent()).thenReturn(true);
        when(vaultUnlocked.hasEconomyProvider()).thenReturn(false);
        when(hooks.getAll()).thenReturn(List.of(vaultUnlocked));
        HavenEconomyService economy = mock(HavenEconomyService.class);
        when(economy.getPreferredAdapter()).thenReturn("money");
        when(economy.isMoneyAvailable()).thenReturn(true);
        when(economy.isItemAvailable()).thenReturn(false);
        HavenDataSource dataSource = mock(HavenDataSource.class);
        when(dataSource.getDataSource()).thenReturn(mock(DataSource.class));
        HavenStorageService storage = mock(HavenStorageService.class);
        HavenCodexService codex = mock(HavenCodexService.class);
        ExecutorService asyncExecutor = mock(ExecutorService.class);
        OpToggleService opToggleService = mock(OpToggleService.class);
        when(opToggleService.isEnabled()).thenReturn(true);
        when(opToggleService.entryCount()).thenReturn(1);

        ServicesManager services = plugin.getServer().getServicesManager();
        when(services.load(HavenEconomyService.class)).thenReturn(economy);
        when(services.load(HavenDataSource.class)).thenReturn(dataSource);
        when(services.load(HavenStorageService.class)).thenReturn(storage);
        when(services.load(HavenCodexService.class)).thenReturn(codex);

        HavenCommand command = new HavenCommand(plugin, config, hooks, asyncExecutor, opToggleService);

        command.onCommand(sender, mock(Command.class), "haven", new String[] {"status"});

        List<String> messages = sentPlainMessages(sender);
        assertTrue(messages.stream().anyMatch(message -> message.contains("Hooks:")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("vaultunlocked")
            && message.contains("MISCONFIGURED") && message.contains("plugin=DETECTED")
            && message.contains("provider=UNAVAILABLE")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("Economy:")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("preferred=money")
            && message.contains("money=READY") && message.contains("item=UNAVAILABLE")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("Services:")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("database")
            && message.contains("READY")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("async")
            && message.contains("READY")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("storage")
            && message.contains("READY")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("codex")
            && message.contains("READY")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("op-toggle")
            && message.contains("ENABLED") && message.contains("entries=1")));
        assertTrue(messages.stream().noneMatch(message -> message.contains("A5B27")));
    }

    @Test
    void helpShowsCommandsAndRequiredPermissions() {
        Plugin plugin = mockPluginWithServices();
        ConfigManager config = mock(ConfigManager.class);
        HavenHookRegistry hooks = mock(HavenHookRegistry.class);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("haven.use")).thenReturn(true);

        HavenCommand command = new HavenCommand(plugin, config, hooks);

        command.onCommand(sender, mock(Command.class), "haven", new String[] {"help"});

        List<String> messages = sentPlainMessages(sender);
        assertTrue(messages.stream().anyMatch(message -> message.contains("/haven status")
            && message.contains("haven.use")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("/haven doctor")
            && message.contains("haven.admin.doctor")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("/haven version")
            && message.contains("haven.use")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("/haven reload")
            && message.contains("haven.admin.reload")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("/haven toggleop")
            && message.contains("configured UUID")
            && message.contains("havencore.toggleop.<code>")));
    }

    @Test
    void doctorRequiresAdminDoctorPermission() {
        HavenCommand command = new HavenCommand(
            mockPluginWithServices(),
            mock(ConfigManager.class),
            mock(HavenHookRegistry.class)
        );
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("haven.admin.doctor")).thenReturn(false);

        command.onCommand(sender, mock(Command.class), "haven", new String[] {"doctor"});

        List<String> messages = sentPlainMessages(sender);
        assertTrue(messages.stream().anyMatch(message -> message.contains("No permission")));
    }

    @Test
    void doctorReportsCoreDiagnosticSummary() throws SQLException {
        Plugin plugin = mockPluginWithServices();
        ConfigManager config = mock(ConfigManager.class);
        stubLoadedConfig(config);
        HavenHookRegistry hooks = mock(HavenHookRegistry.class);
        when(hooks.getAll()).thenReturn(List.of());
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("haven.admin.doctor")).thenReturn(true);
        HavenEconomyService economy = mock(HavenEconomyService.class);
        when(economy.isMoneyAvailable()).thenReturn(true);
        HavenDataSource dataSource = mock(HavenDataSource.class);
        DataSource jdbc = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getDataSource()).thenReturn(jdbc);
        when(jdbc.getConnection()).thenReturn(connection);
        HavenStorageService storage = mock(HavenStorageService.class);
        HavenCodexService codex = mock(HavenCodexService.class);
        ExecutorService asyncExecutor = mock(ExecutorService.class);
        OpToggleService opToggleService = mock(OpToggleService.class);

        ServicesManager services = plugin.getServer().getServicesManager();
        when(services.load(HavenEconomyService.class)).thenReturn(economy);
        when(services.load(HavenDataSource.class)).thenReturn(dataSource);
        when(services.load(HavenStorageService.class)).thenReturn(storage);
        when(services.load(HavenCodexService.class)).thenReturn(codex);

        HavenCommand command = new HavenCommand(plugin, config, hooks, asyncExecutor, opToggleService);

        command.onCommand(sender, mock(Command.class), "haven", new String[] {"doctor"});

        List<String> messages = sentPlainMessages(sender);
        assertTrue(messages.stream().anyMatch(message -> message.contains("HavenCore Doctor")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("PASS") && message.contains("config")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("PASS") && message.contains("database")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("WARN") && message.contains("hooks")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("Summary:")
            && message.contains("pass=") && message.contains("warn=") && message.contains("fail=0")));
        verify(connection).close();
    }

    @Test
    void toggleOpRejectsNonPlayerSendersWithGenericMessage() {
        OpToggleService opToggleService = mock(OpToggleService.class);
        HavenCommand command = new HavenCommand(
            mockPluginWithServices(),
            mock(ConfigManager.class),
            mock(HavenHookRegistry.class),
            null,
            opToggleService
        );
        CommandSender sender = mock(CommandSender.class);

        command.onCommand(sender, mock(Command.class), "haven", new String[] {"toggleop"});

        verify(opToggleService, never()).toggle(any());
        List<String> messages = sentPlainMessages(sender);
        assertTrue(messages.stream().anyMatch(message -> message.contains("not allowed")));
    }

    @Test
    void toggleOpDelegatesToServiceAndReportsNewState() {
        OpToggleService opToggleService = mock(OpToggleService.class);
        Player player = mock(Player.class);
        when(opToggleService.toggle(player)).thenReturn(OpToggleService.ToggleResult.allowed(true));
        HavenCommand command = new HavenCommand(
            mockPluginWithServices(),
            mock(ConfigManager.class),
            mock(HavenHookRegistry.class),
            null,
            opToggleService
        );

        command.onCommand(player, mock(Command.class), "haven", new String[] {"toggleop"});

        verify(opToggleService).toggle(player);
        List<String> messages = sentPlainMessages(player);
        assertTrue(messages.stream().anyMatch(message -> message.contains("Operator mode enabled")));
    }

    @Test
    void toggleOpRefreshesOpToggleConfigBeforeCheckingService() {
        ConfigManager config = mock(ConfigManager.class);
        YamlConfiguration opToggleConfig = new YamlConfiguration();
        opToggleConfig.set("enabled", true);
        opToggleConfig.set("player", "00000000-0000-0000-0000-000000000001");
        opToggleConfig.set("code", "2410a");
        when(config.getOpToggle()).thenReturn(opToggleConfig);
        OpToggleService opToggleService = mock(OpToggleService.class);
        Player player = mock(Player.class);
        when(opToggleService.toggle(player)).thenReturn(OpToggleService.ToggleResult.allowed(true));
        HavenCommand command = new HavenCommand(
            mockPluginWithServices(),
            config,
            mock(HavenHookRegistry.class),
            null,
            opToggleService
        );

        command.onCommand(player, mock(Command.class), "haven", new String[] {"toggleop"});

        InOrder inOrder = inOrder(config, opToggleService);
        inOrder.verify(config).reloadOpToggle();
        inOrder.verify(opToggleService).reload(any(OpToggleSettings.class));
        inOrder.verify(opToggleService).toggle(player);
    }

    @Test
    void toggleOpUsesGenericDeniedMessageWhenServiceRejectsPlayer() {
        OpToggleService opToggleService = mock(OpToggleService.class);
        Player player = mock(Player.class);
        when(opToggleService.toggle(player)).thenReturn(OpToggleService.ToggleResult.denied());
        HavenCommand command = new HavenCommand(
            mockPluginWithServices(),
            mock(ConfigManager.class),
            mock(HavenHookRegistry.class),
            null,
            opToggleService
        );

        command.onCommand(player, mock(Command.class), "haven", new String[] {"toggleop"});

        List<String> messages = sentPlainMessages(player);
        assertTrue(messages.stream().anyMatch(message -> message.contains("not allowed")));
    }

    @Test
    void tabCompletionOnlySuggestsAllowedSubcommands() {
        HavenCommand command = new HavenCommand(
            mockPluginWithServices(),
            mock(ConfigManager.class),
            mock(HavenHookRegistry.class)
        );
        Player sender = mock(Player.class);
        when(sender.hasPermission("haven.use")).thenReturn(true);
        when(sender.hasPermission("haven.admin.reload")).thenReturn(false);
        when(sender.hasPermission("haven.admin.doctor")).thenReturn(false);

        assertEquals(List.of("help", "status", "toggleop", "version"), command.onTabComplete(
            sender, mock(Command.class), "haven", new String[] {""}
        ));

        when(sender.hasPermission("haven.admin.reload")).thenReturn(true);
        when(sender.hasPermission("haven.admin.doctor")).thenReturn(true);

        assertEquals(List.of("doctor", "help", "reload", "status", "toggleop", "version"), command.onTabComplete(
            sender, mock(Command.class), "haven", new String[] {""}
        ));
    }

    private static void stubLoadedConfig(ConfigManager config) {
        when(config.getMain()).thenReturn(new YamlConfiguration());
        when(config.getDatabase()).thenReturn(new YamlConfiguration());
        when(config.getMessages()).thenReturn(new YamlConfiguration());
        when(config.getEconomy()).thenReturn(new YamlConfiguration());
        when(config.getStorage()).thenReturn(new YamlConfiguration());
        when(config.getCodex()).thenReturn(new YamlConfiguration());
        when(config.getHooks()).thenReturn(new YamlConfiguration());
        when(config.getOpToggle()).thenReturn(new YamlConfiguration());
    }

    private static Plugin mockPluginWithServices() {
        Plugin plugin = mock(Plugin.class, RETURNS_DEEP_STUBS);
        PluginMeta meta = mock(PluginMeta.class);
        when(meta.getVersion()).thenReturn("1.0.0");
        when(plugin.getPluginMeta()).thenReturn(meta);
        return plugin;
    }

    private static List<String> sentPlainMessages(CommandSender sender) {
        ArgumentCaptor<Component> messageCaptor = ArgumentCaptor.forClass(Component.class);
        verify(sender, atLeastOnce()).sendMessage(messageCaptor.capture());
        List<String> messages = new ArrayList<>();
        for (Component component : messageCaptor.getAllValues()) {
            messages.add(PlainTextComponentSerializer.plainText().serialize(component));
        }
        return messages;
    }
}
