package dev.invisiblespiders.haven.core.chat;

import dev.invisiblespiders.haven.api.chat.ChatChannel;
import dev.invisiblespiders.haven.api.chat.ChatRecipientType;
import dev.invisiblespiders.haven.core.hook.PlaceholderAPIHook;
import org.bukkit.Server;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatChannelServiceTest {

    @Mock Plugin plugin;
    @Mock Server server;
    @Mock PlaceholderAPIHook papiHook;
    @Mock Player sender;
    @Mock Player recipient;

    ChatSettings settings;
    ChatChannelService service;

    @BeforeEach
    void setup() throws Exception {
        when(plugin.getServer()).thenReturn(server);
        when(sender.getName()).thenReturn("Alice");
        when(sender.getUniqueId()).thenReturn(UUID.randomUUID());

        var config = new org.bukkit.configuration.file.YamlConfiguration();
        config.loadFromString("""
            channels:
              global:
                display-name: "Global"
                format: "<white><player_name>: <message>"
                recipient-type: ALL
                default: true
              staff:
                display-name: "Staff"
                format: "<red>[Staff] <player_name>: <message>"
                recipient-type: PERMISSION
                permission: "haven.staff"
                trigger-prefix: "@"
                default: false
            """);
        settings = ChatSettings.from(config);
        service = new ChatChannelService(settings, papiHook, plugin);
    }

    @Test
    void getPlayerChannel_returnsDefaultForUnknownPlayer() {
        assertThat(service.getPlayerChannel(UUID.randomUUID()).id()).isEqualTo("global");
    }

    @Test
    void setPlayerChannel_changesActiveChannel() {
        UUID uuid = sender.getUniqueId();
        service.setPlayerChannel(uuid, "staff");
        assertThat(service.getPlayerChannel(uuid).id()).isEqualTo("staff");
    }

    @Test
    void getChannel_returnsEmptyForUnknownId() {
        assertThat(service.getChannel("nonexistent")).isEmpty();
    }

    @Test
    void getChannel_returnsChannelById() {
        assertThat(service.getChannel("staff")).isPresent();
        assertThat(service.getChannel("staff").get().id()).isEqualTo("staff");
    }

    @SuppressWarnings("unchecked")
    @Test
    void sendToChannel_ALL_broadcastsToOnlinePlayers() {
        when(server.getOnlinePlayers()).thenReturn((java.util.Collection) java.util.List.of(sender, recipient));
        when(papiHook.isAvailable()).thenReturn(false);
        ChatChannel global = settings.defaultChannel();
        service.sendToChannel(sender, global, "Hello world");
        verify(sender).sendMessage(any(net.kyori.adventure.text.Component.class));
        verify(recipient).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void sendToChannel_PERMISSION_filtersRecipients() {
        when(server.getOnlinePlayers()).thenReturn((java.util.Collection) List.of(sender, recipient));
        when(papiHook.isAvailable()).thenReturn(false);
        when(sender.hasPermission("haven.staff")).thenReturn(true);
        when(recipient.hasPermission("haven.staff")).thenReturn(false);
        ChatChannel staff = settings.channels().get("staff");
        service.sendToChannel(sender, staff, "Staff message");
        verify(sender).sendMessage(any(net.kyori.adventure.text.Component.class));
        verify(recipient, never()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }
}
