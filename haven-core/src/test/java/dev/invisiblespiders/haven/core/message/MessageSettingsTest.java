package dev.invisiblespiders.haven.core.message;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageSettingsTest {

    private static YamlConfiguration load(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try { config.loadFromString(yaml); }
        catch (InvalidConfigurationException e) { throw new RuntimeException(e); }
        return config;
    }

    @Test
    void defaultPresetPresentInJoinPresets() {
        MessageSettings settings = MessageSettings.from(load(""));
        assertThat(settings.joinPresets()).containsKey("default");
        assertThat(settings.joinPresets().get("default").isDefault()).isTrue();
    }

    @Test
    void defaultPresetPresentInQuitPresets() {
        MessageSettings settings = MessageSettings.from(load(""));
        assertThat(settings.quitPresets()).containsKey("default");
    }

    @Test
    void parsesJoinPresetWithUnlockType() {
        MessageSettings settings = MessageSettings.from(load("""
            join-presets:
              default:
                message: "<green>→ <player> joined."
                unlock-type: FREE
              vip-entry:
                message: "<gold><player> has arrived."
                unlock-type: PERMISSION
                permission: "haven.messages.preset.vip-entry"
            """));
        assertThat(settings.joinPresets().get("vip-entry").unlockType()).isEqualTo(UnlockType.PERMISSION);
        assertThat(settings.joinPresets().get("vip-entry").permission()).isEqualTo("haven.messages.preset.vip-entry");
    }

    @Test
    void parsesDeathMessages() {
        MessageSettings settings = MessageSettings.from(load("""
            death-messages:
              FALL:
                - message: "<player> hit the ground too hard."
                - message: "<player> forgot how to land."
                  permission: "haven.messages.premium"
            """));
        var fallMessages = settings.deathMessages().get(EntityDamageEvent.DamageCause.FALL);
        assertThat(fallMessages).hasSize(2);
        assertThat(fallMessages.get(0).isUnrestricted()).isTrue();
        assertThat(fallMessages.get(1).permission()).isEqualTo("haven.messages.premium");
    }

    @Test
    void unknownCausePoolPresentByDefault() {
        MessageSettings settings = MessageSettings.from(load(""));
        assertThat(settings.deathMessages()).containsKey(EntityDamageEvent.DamageCause.CUSTOM);
    }

    @Test
    void filterSettingsParsed() {
        MessageSettings settings = MessageSettings.from(load("""
            filter:
              max-length: 50
              blocked-patterns:
                - "(?i)badword"
            """));
        assertThat(settings.filter().maxLength()).isEqualTo(50);
        assertThat(settings.filter().blockedPatterns()).containsExactly("(?i)badword");
    }
}
