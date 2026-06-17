package dev.invisiblespiders.haven.core.sleep;

import dev.invisiblespiders.haven.api.event.HavenSleepSkipCompleteEvent;
import dev.invisiblespiders.haven.api.event.HavenSleepSkipStartEvent;
import dev.invisiblespiders.haven.api.service.HavenAfkService;
import dev.invisiblespiders.haven.api.service.HavenEventBus;
import dev.invisiblespiders.haven.api.service.HavenSleepService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
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
    final Map<String, Set<UUID>> skippedWith = new ConcurrentHashMap<>();
    final Map<String, State> worldStates = new ConcurrentHashMap<>();
    private final Map<String, Boolean> runtimeEnabled = new ConcurrentHashMap<>();
    private final Map<String, Integer> originalSleepingPercentage = new ConcurrentHashMap<>();

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
        for (String name : settings.worlds()) {
            World world = Bukkit.getWorld(name);
            if (world == null) continue;
            Integer original = world.getGameRuleValue(GameRule.PLAYERS_SLEEPING_PERCENTAGE);
            originalSleepingPercentage.put(name, original != null ? original : 100);
            world.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, 101);
        }
    }

    public void onDisable() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        for (String name : settings.worlds()) {
            World world = Bukkit.getWorld(name);
            if (world == null) continue;
            int original = originalSleepingPercentage.getOrDefault(name, 100);
            world.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, original);
        }
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

    // ── State machine ─────────────────────────────────────────────────────────

    void reevaluate(World world) {
        State current = worldStates.getOrDefault(world.getName(), State.IDLE);
        boolean threshold = meetsThreshold(world);
        int sleeping = getSleepingCount(world);

        switch (current) {
            case IDLE, WAITING -> {
                if (threshold) {
                    transitionToSkipping(world);
                } else {
                    worldStates.put(world.getName(), sleeping > 0 ? State.WAITING : State.IDLE);
                    sendActionBarToWorld(world);
                }
            }
            case SKIPPING -> {
                if (!threshold) transitionToPaused(world);
                // else BukkitRunnable continues ticking
            }
            case PAUSED -> {
                if (threshold) {
                    transitionToSkipping(world);
                } else {
                    sendActionBarToWorld(world);
                }
            }
        }
    }

    private void transitionToSkipping(World world) {
        State previous = worldStates.put(world.getName(), State.SKIPPING);
        if (previous == State.SKIPPING) return; // already skipping, no-op
        int sleeping = getSleepingCount(world);
        int active   = countActive(world);
        skippedWith.computeIfAbsent(world.getName(), k -> ConcurrentHashMap.newKeySet())
            .addAll(sleepingPlayers.getOrDefault(world.getName(), Set.of()));
        eventBus.publish(new HavenSleepSkipStartEvent(world, sleeping, active));
        broadcastToWorld(world, settings.messages().skipStart());
        ensureTickRunning();
    }

    private void transitionToPaused(World world) {
        worldStates.put(world.getName(), State.PAUSED);
        sendActionBarToWorld(world);
        stopTickIfNotNeeded();
    }

    private void ensureTickRunning() {
        if (tickTask != null && !tickTask.isCancelled()) return;
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (String name : settings.worlds()) {
                if (worldStates.getOrDefault(name, State.IDLE) != State.SKIPPING) continue;
                World w = Bukkit.getWorld(name);
                if (w != null) tickWorld(w);
            }
        }, 0L, 1L);
    }

    private void stopTickIfNotNeeded() {
        boolean anySkipping = worldStates.values().stream().anyMatch(s -> s == State.SKIPPING);
        if (!anySkipping && tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    // ── Bukkit event handlers ─────────────────────────────────────────────────

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onBedEnter(org.bukkit.event.player.PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != org.bukkit.event.player.PlayerBedEnterEvent.BedEnterResult.OK) return;
        Player player = event.getPlayer();
        World world   = player.getWorld();
        if (!isEligible(world)) return;
        long time = world.getTime();
        if (time < 12541L || time >= DAWN_TICK) return; // not night

        sleepingPlayers.get(world.getName()).add(player.getUniqueId());
        reevaluate(world);
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onBedLeave(org.bukkit.event.player.PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        World world   = player.getWorld();
        if (!isEligible(world)) return;

        sleepingPlayers.getOrDefault(world.getName(), Set.of()).remove(player.getUniqueId());
        reevaluate(world);
    }

    @org.bukkit.event.EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        World world   = player.getWorld();
        if (!isEligible(world)) return;

        sleepingPlayers.getOrDefault(world.getName(), Set.of()).remove(player.getUniqueId());
        reevaluate(world);
    }

    // ── Skip advancement ──────────────────────────────────────────────────────

    private void tickWorld(World world) {
        long current = world.getTime();
        if (current < 12541L || current >= DAWN_TICK) {
            completeSkip(world);
            return;
        }
        long next = current + computeSpeed(world);
        if (next >= DAWN_TICK) {
            completeSkip(world);
        } else {
            world.setTime(next);
            sendActionBarToWorld(world);
        }
    }

    void completeSkip(World world) {
        world.setTime(0L);

        Set<UUID> slept = skippedWith.getOrDefault(world.getName(), Collections.emptySet());
        for (Player p : world.getPlayers()) {
            UUID uid = p.getUniqueId();
            if (uid != null && slept.contains(uid)) {
                p.setStatistic(org.bukkit.Statistic.TIME_SINCE_REST, 0);
            }
            if (p.isSleeping()) {
                p.wakeup(false);
            }
        }

        // Send skip-complete action bar before clearing state
        String completeMsgTemplate = settings.messages().skipComplete();
        if (completeMsgTemplate != null && !completeMsgTemplate.isBlank()) {
            Component completeBar = MM.deserialize(completeMsgTemplate);
            for (Player p : world.getPlayers()) {
                p.sendActionBar(completeBar);
            }
        }

        worldStates.put(world.getName(), State.IDLE);
        sleepingPlayers.getOrDefault(world.getName(), Set.of()).clear();
        skippedWith.remove(world.getName());
        stopTickIfNotNeeded();
        eventBus.publish(new HavenSleepSkipCompleteEvent(world));
        broadcastToWorld(world, settings.messages().broadcastSkip());
    }

    public boolean forceSkip(World world) {
        if (!isEligible(world)) return false;
        long time = world.getTime();
        if (time < 12541L || time >= DAWN_TICK) return false;
        if (worldStates.getOrDefault(world.getName(), State.IDLE) == State.SKIPPING) return false;
        skippedWith.computeIfAbsent(world.getName(), k -> ConcurrentHashMap.newKeySet())
            .addAll(sleepingPlayers.getOrDefault(world.getName(), Set.of()));
        worldStates.put(world.getName(), State.SKIPPING);
        int sleeping = getSleepingCount(world);
        int active   = countActive(world);
        eventBus.publish(new HavenSleepSkipStartEvent(world, sleeping, active));
        broadcastToWorld(world, settings.messages().skipStart());
        ensureTickRunning();
        return true;
    }

    public boolean toggle(World world) {
        boolean current  = Boolean.TRUE.equals(runtimeEnabled.get(world.getName()));
        boolean newState = !current;
        runtimeEnabled.put(world.getName(), newState);
        if (!newState) {
            sleepingPlayers.getOrDefault(world.getName(), Set.of()).clear();
            worldStates.put(world.getName(), State.IDLE);
            stopTickIfNotNeeded();
        }
        return newState;
    }

    void sendActionBarToWorld(World world) {
        State state = worldStates.getOrDefault(world.getName(), State.IDLE);
        String template = switch (state) {
            case WAITING, SKIPPING -> settings.messages().sleeping();
            case PAUSED -> settings.messages().skipPaused();
            default -> null;
        };
        if (template == null) return;

        int sleeping = getSleepingCount(world);
        int active   = countActive(world);
        String formatted = template
            .replace("<sleeping>", String.valueOf(sleeping))
            .replace("<active>",   String.valueOf(active));
        Component bar = MM.deserialize(formatted);
        for (Player p : world.getPlayers()) {
            p.sendActionBar(bar);
        }
    }

    void broadcastToWorld(World world, String miniMessage) {
        if (miniMessage == null || miniMessage.isBlank()) return;
        Component msg = MM.deserialize(miniMessage);
        for (Player p : world.getPlayers()) {
            p.sendMessage(msg);
        }
    }
}
