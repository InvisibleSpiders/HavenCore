package dev.invisiblespiders.haven.core.command;

import dev.invisiblespiders.haven.api.service.HavenHookRegistry;
import dev.invisiblespiders.haven.api.service.HavenCodexService;
import dev.invisiblespiders.haven.api.service.HavenDataSource;
import dev.invisiblespiders.haven.api.service.HavenEconomyService;
import dev.invisiblespiders.haven.api.service.HavenStorageService;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import dev.invisiblespiders.haven.core.hook.VaultUnlockedHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import io.papermc.paper.plugin.configuration.PluginMeta;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.sql.DataSource;
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
    void statusShowsHookEconomyAndServiceHealth() {
        Plugin plugin = mockPluginWithServices();
        ConfigManager config = mock(ConfigManager.class);
        HavenHookRegistry hooks = mock(HavenHookRegistry.class);
        CommandSender sender = mock(CommandSender.class);
        VaultUnlockedHook vaultUnlocked = mock(VaultUnlockedHook.class);
        when(vaultUnlocked.getId()).thenReturn("vaultunlocked");
        when(vaultUnlocked.isAvailable()).thenReturn(true);
        when(vaultUnlocked.isPluginPresent()).thenReturn(true);
        when(vaultUnlocked.hasEconomyProvider()).thenReturn(true);
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

        ServicesManager services = plugin.getServer().getServicesManager();
        when(services.load(HavenEconomyService.class)).thenReturn(economy);
        when(services.load(HavenDataSource.class)).thenReturn(dataSource);
        when(services.load(HavenStorageService.class)).thenReturn(storage);
        when(services.load(HavenCodexService.class)).thenReturn(codex);

        HavenCommand command = new HavenCommand(plugin, config, hooks, asyncExecutor);

        command.onCommand(sender, mock(Command.class), "haven", new String[] {"status"});

        List<String> messages = sentPlainMessages(sender);
        assertTrue(messages.stream().anyMatch(message -> message.contains("Hooks:")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("vaultunlocked")
            && message.contains("LOADED") && message.contains("plugin=DETECTED")
            && message.contains("provider=READY")));
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
    }

    @Test
    void helpShowsCommandsAndRequiredPermissions() {
        Plugin plugin = mockPluginWithServices();
        ConfigManager config = mock(ConfigManager.class);
        HavenHookRegistry hooks = mock(HavenHookRegistry.class);
        CommandSender sender = mock(CommandSender.class);

        HavenCommand command = new HavenCommand(plugin, config, hooks);

        command.onCommand(sender, mock(Command.class), "haven", new String[] {"help"});

        List<String> messages = sentPlainMessages(sender);
        assertTrue(messages.stream().anyMatch(message -> message.contains("/haven status")
            && message.contains("haven.use")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("/haven version")
            && message.contains("haven.use")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("/haven reload")
            && message.contains("haven.admin.reload")));
    }

    @Test
    void tabCompletionOnlySuggestsAllowedSubcommands() {
        HavenCommand command = new HavenCommand(
            mockPluginWithServices(),
            mock(ConfigManager.class),
            mock(HavenHookRegistry.class)
        );
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("haven.admin.reload")).thenReturn(false);

        assertEquals(List.of("help", "status", "version"), command.onTabComplete(
            sender, mock(Command.class), "haven", new String[] {""}
        ));

        when(sender.hasPermission("haven.admin.reload")).thenReturn(true);

        assertEquals(List.of("help", "reload", "status", "version"), command.onTabComplete(
            sender, mock(Command.class), "haven", new String[] {""}
        ));
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
