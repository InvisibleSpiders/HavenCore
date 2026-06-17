package dev.invisiblespiders.haven.core.sleep;

import dev.invisiblespiders.haven.api.service.HavenEventBus;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SleepAdminCommandTest {

    @Mock Plugin plugin;
    @Mock Server server;
    @Mock HavenEventBus eventBus;
    @Mock Logger logger;
    @Mock World world;
    @Mock Player sender;

    SleepManager manager;
    SleepAdminCommand command;

    @BeforeEach
    void setup() {
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(logger);
        when(world.getName()).thenReturn("world");
        when(sender.getWorld()).thenReturn(world);
        SleepSettings settings = SleepSettings.from(new YamlConfiguration());
        manager = new SleepManager(settings, null, eventBus, plugin, logger);
        command = new SleepAdminCommand(manager, plugin);
    }

    @Test
    void statusSendsMessageToSender() {
        when(world.getPlayers()).thenReturn(List.of());
        when(server.getWorld("world")).thenReturn(world);

        command.execute(sender, new String[]{"status"});

        verify(sender, atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void toggleDisablesWorldAndReturnsNewState() {
        boolean result = manager.toggle(world);
        assertThat(result).isFalse();
        assertThat(manager.isEligible(world)).isFalse();
    }

    @Test
    void toggleReEnablesWorldAfterDisable() {
        manager.toggle(world); // disable
        boolean result = manager.toggle(world); // re-enable
        assertThat(result).isTrue();
        assertThat(manager.isEligible(world)).isTrue();
    }

    @Test
    void tabCompleteLevel1ReturnsSubcommands() {
        List<String> completions = command.tabComplete(sender, new String[]{""});
        assertThat(completions).containsExactlyInAnyOrder("skip", "toggle", "status");
    }

    @Test
    void tabCompleteFiltersByPrefix() {
        List<String> completions = command.tabComplete(sender, new String[]{"sk"});
        assertThat(completions).containsExactly("skip");
    }
}
