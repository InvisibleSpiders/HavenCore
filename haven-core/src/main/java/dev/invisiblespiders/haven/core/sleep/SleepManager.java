package dev.invisiblespiders.haven.core.sleep;

import dev.invisiblespiders.haven.api.event.HavenSleepSkipCompleteEvent;
import dev.invisiblespiders.haven.api.event.HavenSleepSkipStartEvent;
import dev.invisiblespiders.haven.api.service.HavenAfkService;
import dev.invisiblespiders.haven.api.service.HavenEventBus;
import dev.invisiblespiders.haven.api.service.HavenSleepService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class SleepManager implements HavenSleepService, Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final long DAWN_TICK = 23460L;

    enum State { IDLE, WAITING, SKIPPING, PAUSED }

    private final SleepSettings settings;
    private final @Nullable HavenAfkService afkService;
    private final HavenEventBus eventBus;
    private final Plugin plugin;
    private final Logger logger;

    // Keyed by world name for safe map lookup without holding World references.
    final Map<String, Set<UUID>> sleepingPlayers = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> skippedWith = new ConcurrentHashMap<>();
    final Map<String, State> worldStates = new ConcurrentHashMap<>();
    private final Map<String, Boolean> runtimeEnabled = new ConcurrentHashMap<>();

    private @Nullable BukkitTask tickTask;

    public SleepManager(SleepSettings settings, @Nullable HavenAfkService afkService,
                        HavenEventBus eventBus, Plugin plugin, Logger logger) {
        this.settings = settings;
        this.afkService = afkService;
        this.eventBus = eventBus;
        this.plugin = plugin;
        this.logger = logger;
        for (String name : settings.worlds()) {
            worldStates.put(name, State.IDLE);
            sleepingPlayers.put(name, ConcurrentHashMap.newKeySet());
            runtimeEnabled.put(name, Boolean.TRUE);
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void onEnable() {
        // sleeping-ignored flag wired in Task 5 when tick loop starts
    }

    public void onDisable() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
    }

    // ── HavenSleepService ─────────────────────────────────────────────────────

    @Override
    public boolean isSkipping(World world) {
        return worldStates.getOrDefault(world.getName(), State.IDLE) == State.SKIPPING;
    }

    @Override
    public int getSleepingCount(World world) {
        return sleepingPlayers.getOrDefault(world.getName(), Set.of()).size();
    }

    @Override
    public int getActiveCount(World world) {
        return countActive(world);
    }

    // ── Internal counting ─────────────────────────────────────────────────────

    boolean isEligible(World world) {
        return settings.worlds().contains(world.getName())
            && Boolean.TRUE.equals(runtimeEnabled.get(world.getName()));
    }

    int countActive(World world) {
        return (int) world.getPlayers().stream()
            .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
            .filter(p -> !p.hasPermission("haven.sleep.bypass"))
            .filter(p -> afkService == null || !afkService.isAfk(p.getUniqueId()))
            .count();
    }

    boolean meetsThreshold(World world) {
        int sleeping = getSleepingCount(world);
        if (sleeping == 0) return false;
        int active = countActive(world);
        if (active == 0) return false;
        boolean countOk   = settings.minCount() <= 0  || sleeping >= settings.minCount();
        boolean percentOk = settings.minPercent() <= 0
            || sleeping >= (int) Math.ceil(active * settings.minPercent() / 100.0);
        return countOk && percentOk;
    }

    long computeSpeed(World world) {
        int sleeping = getSleepingCount(world);
        int active   = countActive(world);
        double ratio = active > 0 ? Math.max(0.0, Math.min(1.0, (double) sleeping / active)) : 1.0;
        return settings.minSpeed() + Math.round((settings.maxSpeed() - settings.minSpeed()) * ratio);
    }

    State getState(World world) {
        return worldStates.getOrDefault(world.getName(), State.IDLE);
    }

    SleepSettings getSettings() { return settings; }

    // ── Placeholder stubs (filled in Tasks 5–7) ───────────────────────────────

    void reevaluate(World world) { /* Task 5 */ }

    private void transitionToSkipping(World world) { /* Task 5 */ }

    private void transitionToPaused(World world) { /* Task 5 */ }

    private void ensureTickRunning() { /* Task 5 */ }

    private void stopTickIfNotNeeded() { /* Task 5 */ }

    private void tickWorld(World world) { /* Task 6 */ }

    void completeSkip(World world) { /* Task 6 */ }

    public void forceSkip(World world) { /* Task 6 */ }

    public boolean toggle(World world) { return true; /* Task 6 */ }

    void sendActionBarToWorld(World world) { /* Task 7 */ }

    void broadcastToWorld(World world, String miniMessage) { /* Task 7 */ }
}
