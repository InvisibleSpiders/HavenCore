package dev.invisiblespiders.haven.core.command;

import dev.invisiblespiders.haven.api.service.HavenHookRegistry;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

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
}
