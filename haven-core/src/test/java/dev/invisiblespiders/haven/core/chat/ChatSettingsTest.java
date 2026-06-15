package dev.invisiblespiders.haven.core.chat;

import dev.invisiblespiders.haven.api.chat.ChatRecipientType;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSettingsTest {

    private static YamlConfiguration load(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try { config.loadFromString(yaml); }
        catch (InvalidConfigurationException e) { throw new RuntimeException(e); }
        return config;
    }

    @Test
    void parsesGlobalChannel() {
        ChatSettings settings = ChatSettings.from(load("""
            channels:
              global:
                display-name: "Global"
                format: "<white><player_name>: <message>"
                recipient-type: ALL
                default: true
            """));
        assertThat(settings.channels()).containsKey("global");
        var global = settings.channels().get("global");
        assertThat(global.recipientType()).isEqualTo(ChatRecipientType.ALL);
        assertThat(global.isDefault()).isTrue();
        assertThat(global.permission()).isNull();
    }

    @Test
    void parsesStaffChannelWithPermissionAndPrefix() {
        ChatSettings settings = ChatSettings.from(load("""
            channels:
              staff:
                display-name: "Staff"
                format: "<red>[Staff] <player_name>: <message>"
                recipient-type: PERMISSION
                permission: "haven.staff"
                trigger-prefix: "@"
                default: false
            """));
        var staff = settings.channels().get("staff");
        assertThat(staff.recipientType()).isEqualTo(ChatRecipientType.PERMISSION);
        assertThat(staff.permission()).isEqualTo("haven.staff");
        assertThat(staff.triggerPrefix()).isEqualTo("@");
        assertThat(staff.hasTriggerPrefix()).isTrue();
    }

    @Test
    void defaultChannelResolved() {
        ChatSettings settings = ChatSettings.from(load("""
            channels:
              global:
                display-name: "Global"
                format: "<white><player_name>: <message>"
                recipient-type: ALL
                default: true
              staff:
                display-name: "Staff"
                format: "<red><player_name>: <message>"
                recipient-type: PERMISSION
                permission: "haven.staff"
                default: false
            """));
        assertThat(settings.defaultChannel()).isNotNull();
        assertThat(settings.defaultChannel().id()).isEqualTo("global");
    }

    @Test
    void marketConfigParsedWithDefaults() {
        ChatSettings settings = ChatSettings.from(load(""));
        assertThat(settings.market().slotInterval()).isEqualTo(300);
        assertThat(settings.market().playerCooldown()).isEqualTo(600);
        assertThat(settings.market().warpCommand()).isEqualTo("/warp %s");
    }

    @Test
    void prefixChannelMapBuilt() {
        ChatSettings settings = ChatSettings.from(load("""
            channels:
              staff:
                display-name: "Staff"
                format: "<red><player_name>: <message>"
                recipient-type: PERMISSION
                permission: "haven.staff"
                trigger-prefix: "@"
                default: false
            """));
        assertThat(settings.prefixChannels()).containsKey("@");
        assertThat(settings.prefixChannels().get("@").id()).isEqualTo("staff");
    }
}
