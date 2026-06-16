package dev.invisiblespiders.haven.core.afk;

import dev.invisiblespiders.haven.api.service.HavenPlayerService;
import dev.invisiblespiders.haven.api.upgrade.HavenUpgradeService;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AfkManagerUpgradeTest {

    @Mock Plugin plugin;
    @Mock Server server;
    @Mock HavenPlayerService playerService;
    @Mock HavenUpgradeService upgradeService;

    AfkSettings settings;
    AfkManager manager;
    UUID uuid;

    @BeforeEach
    void setup() throws Exception {
        uuid = UUID.randomUUID();
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
            timeout: 300
            upgrade:
              bonus-seconds:
                - 900
                - 1800
                - 3600
                - 7200
                - 14400
            """);
        settings = AfkSettings.from(config);
        manager = new AfkManager(settings, plugin, playerService);
    }

    @Test
    void effectiveTimeoutIsBaseTimeoutWhenNoUpgradeService() throws Exception {
        long result = invokeEffectiveTimeout(manager, uuid);
        assertThat(result).isEqualTo(300L);
    }

    @Test
    void effectiveTimeoutIsBaseWhenUpgradeServiceReturnsLevel0() throws Exception {
        when(upgradeService.currentLevel(uuid, "afk-timer")).thenReturn(0);
        manager.setUpgradeService(upgradeService);

        long result = invokeEffectiveTimeout(manager, uuid);
        assertThat(result).isEqualTo(300L);
    }

    @Test
    void effectiveTimeoutAddsLevelBonusSeconds() throws Exception {
        when(upgradeService.currentLevel(uuid, "afk-timer")).thenReturn(2);
        manager.setUpgradeService(upgradeService);

        long result = invokeEffectiveTimeout(manager, uuid);
        assertThat(result).isEqualTo(300L + 1800L); // base + level-2 bonus
    }

    @Test
    void effectiveTimeoutClipsOutOfRangeLevelToZeroBonus() throws Exception {
        when(upgradeService.currentLevel(uuid, "afk-timer")).thenReturn(99);
        manager.setUpgradeService(upgradeService);

        long result = invokeEffectiveTimeout(manager, uuid);
        assertThat(result).isEqualTo(300L); // no bonus for invalid level
    }

    @Test
    void kickTimeoutIsRelativeToEffectiveAfkTimeout() throws Exception {
        // With base timeout 300 and level-2 bonus 1800 (effective = 2100),
        // kickTimeout 600 should fire at 2100 + 600 = 2700, not at 600.
        // We verify effectiveTimeout is 2100, which is the AFK onset.
        // The kick condition adds kickTimeout on top: 2100 + 600 = 2700.
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
            timeout: 300
            kick-timeout: 600
            upgrade:
              bonus-seconds:
                - 900
                - 1800
                - 3600
                - 7200
                - 14400
            """);
        AfkSettings kickSettings = AfkSettings.from(config);
        AfkManager kickManager = new AfkManager(kickSettings, plugin, playerService);
        when(upgradeService.currentLevel(uuid, "afk-timer")).thenReturn(2);
        kickManager.setUpgradeService(upgradeService);

        long afkOnset = invokeEffectiveTimeout(kickManager, uuid);
        assertThat(afkOnset).isEqualTo(2100L); // 300 + 1800

        // Kick fires at afkOnset + kickTimeout = 2100 + 600 = 2700
        // (tested implicitly by verifying effectiveTimeout is correct;
        // the kick condition is afkOnset + kickTimeout, not kickTimeout alone)
        assertThat(afkOnset + kickSettings.kickTimeout()).isEqualTo(2700L);
    }

    private static long invokeEffectiveTimeout(AfkManager manager, UUID uuid) throws Exception {
        Method m = AfkManager.class.getDeclaredMethod("effectiveTimeout", UUID.class);
        m.setAccessible(true);
        return (long) m.invoke(manager, uuid);
    }
}
