package dev.invisiblespiders.haven.core.sleep;

import dev.invisiblespiders.haven.api.service.HavenAfkService;
import dev.invisiblespiders.haven.api.service.HavenEventBus;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SleepManagerTest {

    @Mock Plugin plugin;
    @Mock Server server;
    @Mock BukkitScheduler scheduler;
    @Mock BukkitTask tickTask;
    @Mock HavenAfkService afkService;
    @Mock HavenEventBus eventBus;
    @Mock Logger logger;
    @Mock World world;
    @Mock Player player1;
    @Mock Player player2;

    SleepSettings settings;
    SleepManager manager;

    @BeforeEach
    void setup() {
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(logger);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
            .thenReturn(tickTask);
        when(world.getName()).thenReturn("world");
        settings = SleepSettings.from(new YamlConfiguration()); // min-count=1, min-percent=0, min=40, max=200
        manager = new SleepManager(settings, afkService, eventBus, plugin, logger);
    }

    // ── Active count ──────────────────────────────────────────────────────────

    @Test
    void countActive_excludesSpectators() {
        when(world.getPlayers()).thenReturn(List.of(player1));
        when(player1.getGameMode()).thenReturn(GameMode.SPECTATOR);
        assertThat(manager.countActive(world)).isZero();
    }

    @Test
    void countActive_excludesBypassPermission() {
        when(world.getPlayers()).thenReturn(List.of(player1));
        when(player1.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player1.hasPermission("haven.sleep.bypass")).thenReturn(true);
        assertThat(manager.countActive(world)).isZero();
    }

    @Test
    void countActive_excludesAfkPlayers() {
        UUID uuid = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1));
        when(player1.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player1.hasPermission("haven.sleep.bypass")).thenReturn(false);
        when(player1.getUniqueId()).thenReturn(uuid);
        when(afkService.isAfk(uuid)).thenReturn(true);
        assertThat(manager.countActive(world)).isZero();
    }

    @Test
    void countActive_countsEligiblePlayers() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1, player2));
        setupSurvivalPlayer(player1, uuid1, false);
        setupSurvivalPlayer(player2, uuid2, false);
        assertThat(manager.countActive(world)).isEqualTo(2);
    }

    @Test
    void countActive_worksWithNullAfkService() {
        SleepManager noAfk = new SleepManager(settings, null, eventBus, plugin, logger);
        UUID uuid = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1));
        when(player1.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player1.hasPermission("haven.sleep.bypass")).thenReturn(false);
        when(player1.getUniqueId()).thenReturn(uuid);
        assertThat(noAfk.countActive(world)).isEqualTo(1);
    }

    // ── Speed ─────────────────────────────────────────────────────────────────

    @Test
    void computeSpeed_returnsMinWhenNoSleepers() {
        UUID uuid = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1));
        setupSurvivalPlayer(player1, uuid, false);
        // 0 sleeping / 1 active → ratio 0 → minSpeed
        assertThat(manager.computeSpeed(world)).isEqualTo(40L);
    }

    @Test
    void computeSpeed_returnsMaxWhenAllSleep() {
        UUID uuid = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1));
        setupSurvivalPlayer(player1, uuid, false);
        manager.sleepingPlayers.get("world").add(uuid); // 1/1 = 100%
        assertThat(manager.computeSpeed(world)).isEqualTo(200L);
    }

    @Test
    void computeSpeed_interpolatesMidpoint() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1, player2));
        setupSurvivalPlayer(player1, uuid1, false);
        setupSurvivalPlayer(player2, uuid2, false);
        manager.sleepingPlayers.get("world").add(uuid1); // 1/2 = 50%
        // 40 + round((200-40) * 0.5) = 40 + 80 = 120
        assertThat(manager.computeSpeed(world)).isEqualTo(120L);
    }

    // ── Threshold ─────────────────────────────────────────────────────────────

    @Test
    void meetsThreshold_falseWhenNoOneSleeping() {
        UUID uuid = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1));
        setupSurvivalPlayer(player1, uuid, false);
        assertThat(manager.meetsThreshold(world)).isFalse();
    }

    @Test
    void meetsThreshold_trueWhenMinCountOneSleeping() {
        UUID uuid = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1));
        setupSurvivalPlayer(player1, uuid, false);
        manager.sleepingPlayers.get("world").add(uuid);
        assertThat(manager.meetsThreshold(world)).isTrue();
    }

    @Test
    void meetsThreshold_respectsMinPercent() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("threshold.min-count", 0);
        config.set("threshold.min-percent", 50);
        SleepManager strictManager = new SleepManager(
            SleepSettings.from(config), afkService, eventBus, plugin, logger);

        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1, player2));
        setupSurvivalPlayer(player1, uuid1, false);
        setupSurvivalPlayer(player2, uuid2, false);

        // 1 sleeping / 2 active = 50% — exactly meets 50% threshold
        strictManager.sleepingPlayers.get("world").add(uuid1);
        assertThat(strictManager.meetsThreshold(world)).isTrue();
    }

    @Test
    void meetsThreshold_falseWhenBelowMinPercent() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("threshold.min-count", 0);
        config.set("threshold.min-percent", 75);
        SleepManager strictManager = new SleepManager(
            SleepSettings.from(config), afkService, eventBus, plugin, logger);

        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1, player2));
        setupSurvivalPlayer(player1, uuid1, false);
        setupSurvivalPlayer(player2, uuid2, false);

        // 1/2 = 50%, below 75%
        strictManager.sleepingPlayers.get("world").add(uuid1);
        assertThat(strictManager.meetsThreshold(world)).isFalse();
    }

    // ── Eligibility ───────────────────────────────────────────────────────────

    @Test
    void isEligible_trueForConfiguredWorld() {
        assertThat(manager.isEligible(world)).isTrue();
    }

    @Test
    void isEligible_falseForUnknownWorld() {
        World other = mock(World.class);
        when(other.getName()).thenReturn("world_nether");
        assertThat(manager.isEligible(other)).isFalse();
    }

    // ── State machine ─────────────────────────────────────────────────────────

    @Test
    void reevaluate_transitionsIdleToWaitingWhenSomeoneSleepsButBelowThreshold() {
        // With min-count=1 any single sleeper meets threshold, so test with min-count=2
        YamlConfiguration config = new YamlConfiguration();
        config.set("threshold.min-count", 2);
        SleepManager m = new SleepManager(SleepSettings.from(config), afkService, eventBus, plugin, logger);

        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1, player2));
        setupSurvivalPlayer(player1, uuid1, false);
        setupSurvivalPlayer(player2, uuid2, false);

        m.sleepingPlayers.get("world").add(uuid1); // 1 sleeping, min-count=2 → WAITING
        m.reevaluate(world);

        assertThat(m.getState(world)).isEqualTo(SleepManager.State.WAITING);
    }

    @Test
    void reevaluate_transitionsWaitingToIdleWhenNoOneSleeping() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("threshold.min-count", 2);
        SleepManager m = new SleepManager(SleepSettings.from(config), afkService, eventBus, plugin, logger);

        when(world.getPlayers()).thenReturn(List.of(player1));
        setupSurvivalPlayer(player1, UUID.randomUUID(), false);

        // Nothing sleeping → IDLE
        m.reevaluate(world);
        assertThat(m.getState(world)).isEqualTo(SleepManager.State.IDLE);
    }

    @Test
    void reevaluate_publishesStartEventWhenThresholdFirstMet() {
        UUID uuid = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1));
        setupSurvivalPlayer(player1, uuid, false);
        when(world.getTime()).thenReturn(13000L); // night

        manager.sleepingPlayers.get("world").add(uuid); // 1/1 = meets min-count=1
        manager.reevaluate(world);

        verify(eventBus).publish(any(dev.invisiblespiders.haven.api.event.HavenSleepSkipStartEvent.class));
        assertThat(manager.getState(world)).isEqualTo(SleepManager.State.SKIPPING);
    }

    @Test
    void reevaluate_transitionsSkippingToPausedWhenThresholdDrops() {
        UUID uuid = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1));
        setupSurvivalPlayer(player1, uuid, false);
        when(world.getTime()).thenReturn(13000L);

        // Enter skipping
        manager.sleepingPlayers.get("world").add(uuid);
        manager.reevaluate(world);
        assertThat(manager.getState(world)).isEqualTo(SleepManager.State.SKIPPING);

        // Player wakes — below threshold
        manager.sleepingPlayers.get("world").remove(uuid);
        manager.reevaluate(world);
        assertThat(manager.getState(world)).isEqualTo(SleepManager.State.PAUSED);
    }

    @Test
    void reevaluate_resumesFromPausedWhenThresholdMetAgain() {
        UUID uuid = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1));
        setupSurvivalPlayer(player1, uuid, false);
        when(world.getTime()).thenReturn(13000L);

        // Skip → Pause → Resume
        manager.sleepingPlayers.get("world").add(uuid);
        manager.reevaluate(world); // SKIPPING
        manager.sleepingPlayers.get("world").remove(uuid);
        manager.reevaluate(world); // PAUSED
        manager.sleepingPlayers.get("world").add(uuid);
        manager.reevaluate(world); // back to SKIPPING

        assertThat(manager.getState(world)).isEqualTo(SleepManager.State.SKIPPING);
    }

    // ── Skip completion ───────────────────────────────────────────────────────

    @Test
    void completeSkip_setsTimeTo0AndTransitionsToIdle() {
        when(world.getPlayers()).thenReturn(List.of());
        manager.worldStates.put("world", SleepManager.State.SKIPPING);

        manager.completeSkip(world);

        verify(world).setTime(0L);
        assertThat(manager.getState(world)).isEqualTo(SleepManager.State.IDLE);
    }

    @Test
    void completeSkip_resetsInsomniaOnlyForSleepers() {
        UUID sleptUuid  = UUID.randomUUID();
        UUID awakeUuid  = UUID.randomUUID();

        when(player1.getUniqueId()).thenReturn(sleptUuid);
        when(player2.getUniqueId()).thenReturn(awakeUuid);
        when(player1.isSleeping()).thenReturn(false);
        when(player2.isSleeping()).thenReturn(false);
        when(world.getPlayers()).thenReturn(List.of(player1, player2));

        manager.worldStates.put("world", SleepManager.State.SKIPPING);
        manager.skippedWith.put("world", new java.util.HashSet<>(Set.of(sleptUuid)));

        manager.completeSkip(world);

        verify(player1).setStatistic(org.bukkit.Statistic.TIME_SINCE_REST, 0);
        verify(player2, never()).setStatistic(any(), anyInt());
    }

    @Test
    void completeSkip_ejectsPlayersStillInBed() {
        when(world.getPlayers()).thenReturn(List.of(player1, player2));
        when(player1.isSleeping()).thenReturn(true);
        when(player2.isSleeping()).thenReturn(false);

        manager.worldStates.put("world", SleepManager.State.SKIPPING);

        manager.completeSkip(world);

        verify(player1).wakeup(false);
        verify(player2, never()).wakeup(anyBoolean());
    }

    @Test
    void completeSkip_publishesCompleteEvent() {
        when(world.getPlayers()).thenReturn(List.of());
        manager.worldStates.put("world", SleepManager.State.SKIPPING);

        manager.completeSkip(world);

        verify(eventBus).publish(any(dev.invisiblespiders.haven.api.event.HavenSleepSkipCompleteEvent.class));
    }

    @Test
    void completeSkip_clearsSleepingPlayersSet() {
        UUID uuid = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of());
        manager.worldStates.put("world", SleepManager.State.SKIPPING);
        manager.sleepingPlayers.get("world").add(uuid);

        manager.completeSkip(world);

        assertThat(manager.sleepingPlayers.get("world")).isEmpty();
    }

    // ── Player feedback ───────────────────────────────────────────────────────

    @Test
    void sendActionBar_sendsFormattedMessageToWorldPlayers() {
        UUID uuid = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1));
        setupSurvivalPlayer(player1, uuid, false);
        manager.sleepingPlayers.get("world").add(uuid);
        manager.worldStates.put("world", SleepManager.State.WAITING);

        manager.sendActionBarToWorld(world);

        verify(player1).sendActionBar(any(Component.class));
    }

    @Test
    void sendActionBar_sendsNothingForIdleState() {
        when(world.getPlayers()).thenReturn(List.of(player1));
        manager.worldStates.put("world", SleepManager.State.IDLE);

        manager.sendActionBarToWorld(world);

        verify(player1, never()).sendActionBar(any(Component.class));
    }

    @Test
    void broadcastToWorld_sendsMessageToAllPlayers() {
        when(world.getPlayers()).thenReturn(List.of(player1, player2));

        manager.broadcastToWorld(world, "<green>Test broadcast");

        verify(player1).sendMessage(any(Component.class));
        verify(player2).sendMessage(any(Component.class));
    }

    // Helper

    private void setupSurvivalPlayer(Player player, UUID uuid, boolean afk) {
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.hasPermission("haven.sleep.bypass")).thenReturn(false);
        when(player.getUniqueId()).thenReturn(uuid);
        when(afkService.isAfk(uuid)).thenReturn(afk);
    }
}
