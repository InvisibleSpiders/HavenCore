package dev.invisiblespiders.haven.core.afk;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AfkSettingsTest {

    private static YamlConfiguration load(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try { config.loadFromString(yaml); }
        catch (InvalidConfigurationException e) { throw new RuntimeException(e); }
        return config;
    }

    @Test
    void parsesDefaultsFromEmptyConfig() {
        AfkSettings settings = AfkSettings.from(load(""));
        assertThat(settings.timeout()).isEqualTo(300);
        assertThat(settings.kickTimeout()).isEqualTo(1800);
        assertThat(settings.strictMovement()).isTrue();
        assertThat(settings.activityEvents().movement()).isTrue();
        assertThat(settings.activityEvents().keyboardInput()).isTrue();
        assertThat(settings.activityEvents().chat()).isTrue();
        assertThat(settings.activityEvents().commands()).isTrue();
        assertThat(settings.activityEvents().interact()).isTrue();
        assertThat(settings.detection().minRotationDelta()).isEqualTo(1.5f);
        assertThat(settings.detection().patternMinIdleSeconds()).isEqualTo(30);
        assertThat(settings.detection().patternAlert()).isTrue();
        assertThat(settings.detection().patternAlertPermission()).isEqualTo("haven.afk.alerts");
    }

    @Test
    void parsesCustomValues() {
        AfkSettings settings = AfkSettings.from(load("""
            timeout: 120
            kick-timeout: 600
            strict-movement: false
            activity-events:
              movement: false
              keyboard-input: false
              chat: true
              commands: false
              interact: false
            detection:
              min-rotation-delta: 2.0
              pattern-min-idle-seconds: 45
              pattern-alert: false
              pattern-alert-permission: "custom.perm"
            """));
        assertThat(settings.timeout()).isEqualTo(120);
        assertThat(settings.kickTimeout()).isEqualTo(600);
        assertThat(settings.strictMovement()).isFalse();
        assertThat(settings.activityEvents().movement()).isFalse();
        assertThat(settings.activityEvents().keyboardInput()).isFalse();
        assertThat(settings.activityEvents().chat()).isTrue();
        assertThat(settings.detection().minRotationDelta()).isEqualTo(2.0f);
        assertThat(settings.detection().patternMinIdleSeconds()).isEqualTo(45);
        assertThat(settings.detection().patternAlert()).isFalse();
        assertThat(settings.detection().patternAlertPermission()).isEqualTo("custom.perm");
    }

    @Test
    void messageDefaultsPresent() {
        AfkSettings settings = AfkSettings.from(load(""));
        assertThat(settings.messages().afkBroadcast()).isNotBlank();
        assertThat(settings.messages().returnBroadcast()).isNotBlank();
        assertThat(settings.messages().actionBar()).isNotBlank();
        assertThat(settings.messages().kickReason()).isNotBlank();
    }
}
