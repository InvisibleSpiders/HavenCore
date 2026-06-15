package dev.invisiblespiders.haven.core.message;

import dev.invisiblespiders.haven.api.model.HavenPlayer;
import dev.invisiblespiders.haven.api.service.HavenPlayerService;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlayerMessageServiceTest {

    @Mock HavenPlayerService playerService;
    @Mock Player player;
    @Mock HavenPlayer havenPlayer;

    UUID uuid;
    MessageSettings settings;
    PlayerMessageService service;

    private static YamlConfiguration load(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try { config.loadFromString(yaml); }
        catch (InvalidConfigurationException e) { throw new RuntimeException(e); }
        return config;
    }

    @BeforeEach
    void setup() {
        uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("TestPlayer");
        when(playerService.getCached(uuid)).thenReturn(Optional.of(havenPlayer));
        when(playerService.save(havenPlayer)).thenReturn(CompletableFuture.completedFuture(null));

        settings = MessageSettings.from(load("""
            join-presets:
              default:
                message: "<green>→ <player> joined."
                unlock-type: FREE
                default: true
              vip-entry:
                message: "<gold><player> the VIP arrived."
                unlock-type: PERMISSION
                permission: "haven.messages.preset.vip-entry"
            quit-presets:
              default:
                message: "<gray>← <player> left."
                unlock-type: FREE
                default: true
            afk-presets:
              default:
                message: "<gray><player> is AFK."
                unlock-type: FREE
                default: true
            """));
        service = new PlayerMessageService(settings, playerService,
                new MiniMessageSanitizer(FilterSettings.defaults()));
    }

    @Test
    void joinMessage_usesDefaultWhenNoSelection() {
        when(havenPlayer.getData("haven-core", "join-msg-type")).thenReturn(Optional.empty());
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(service.getJoinMessage(player));
        assertThat(plain).contains("TestPlayer").contains("joined");
    }

    @Test
    void joinMessage_usesCustomWhenSet() {
        when(havenPlayer.getData("haven-core", "join-msg-type")).thenReturn(Optional.of("custom"));
        when(havenPlayer.getData("haven-core", "join-msg-custom"))
                .thenReturn(Optional.of("TestPlayer arrives!"));
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(service.getJoinMessage(player));
        assertThat(plain).isEqualTo("TestPlayer arrives!");
    }

    @Test
    void joinMessage_usesPresetWhenSelected() {
        when(havenPlayer.getData("haven-core", "join-msg-type")).thenReturn(Optional.of("preset"));
        when(havenPlayer.getData("haven-core", "join-msg-preset")).thenReturn(Optional.of("vip-entry"));
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(service.getJoinMessage(player));
        assertThat(plain).contains("VIP").contains("TestPlayer");
    }

    @Test
    void unlockPreset_addsToUnlockedList() {
        when(havenPlayer.getData("haven-core", "unlocked-presets")).thenReturn(Optional.empty());
        service.unlockPreset(uuid, "chicken-slayer");
        verify(havenPlayer).setData("haven-core", "unlocked-presets", "chicken-slayer");
    }

    @Test
    void unlockPreset_appendsToExistingList() {
        when(havenPlayer.getData("haven-core", "unlocked-presets"))
                .thenReturn(Optional.of("wave"));
        service.unlockPreset(uuid, "chicken-slayer");
        verify(havenPlayer).setData("haven-core", "unlocked-presets", "wave,chicken-slayer");
    }

    @Test
    void getUnlockedPresets_returnsEmptyWhenNone() {
        when(havenPlayer.getData("haven-core", "unlocked-presets")).thenReturn(Optional.empty());
        assertThat(service.getUnlockedPresets(uuid)).isEmpty();
    }

    @Test
    void getUnlockedPresets_parsesCommaSeparated() {
        when(havenPlayer.getData("haven-core", "unlocked-presets"))
                .thenReturn(Optional.of("wave,chicken-slayer"));
        assertThat(service.getUnlockedPresets(uuid)).containsExactly("wave", "chicken-slayer");
    }
}
