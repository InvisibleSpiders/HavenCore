package dev.invisiblespiders.haven.core.afk;

import dev.invisiblespiders.haven.api.service.HavenAfkService;
import dev.invisiblespiders.haven.api.service.HavenPlayerService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class AfkManager implements HavenAfkService {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int PATTERN_SAMPLE_SIZE = 10;
    private static final double PATTERN_MAX_STD_DEV_MS = 200.0;

    private final AfkSettings settings;
    private final Plugin plugin;
    private final HavenPlayerService playerService;
    private final Logger logger;

    // Late-injected after init to avoid circular dependency.
    // Type is Object for now; will be replaced with TabManager in Task 9.
    private @Nullable Object tabManager;

    // Late-injected; will be wired to HavenMessageService in Plan 3 Task 8.
    // Stored as Runnable-returning supplier to avoid forward-reference compile error.
    private @Nullable java.util.function.Function<Player, Component> messageServiceDelegate;

    private final ConcurrentHashMap<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> afkState = new ConcurrentHashMap<>();
    // Stores last N activity reset timestamps per player for pattern detection
    private final ConcurrentHashMap<UUID, Deque<Long>> activityTimestamps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, BukkitTask> actionBarTasks = new ConcurrentHashMap<>();

    private @Nullable BukkitTask schedulerTask;

    public AfkManager(AfkSettings settings, Plugin plugin, HavenPlayerService playerService) {
        this.settings = settings;
        this.plugin = plugin;
        this.playerService = playerService;
        this.logger = plugin.getLogger();
    }

    public void start() {
        schedulerTask = new BukkitRunnable() {
            @Override public void run() { tick(); }
        }.runTaskTimer(plugin, 100L, 100L); // every 5 seconds (100 ticks)
    }

    public void stop() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
        actionBarTasks.values().forEach(BukkitTask::cancel);
        actionBarTasks.clear();
    }

    /**
     * Called by HavenCore after HavenMessageService is initialised (Plan 3 Task 8).
     * The delegate should return the formatted AFK broadcast component for a player.
     */
    public void setMessageServiceDelegate(@Nullable java.util.function.Function<Player, Component> delegate) {
        this.messageServiceDelegate = delegate;
    }

    /**
     * Placeholder for TabManager injection (Task 9).
     * Parameter is Object to avoid a forward-reference compile error;
     * will be changed to TabManager once that class exists.
     */
    public void setTabManager(@Nullable Object tabManager) {
        this.tabManager = tabManager;
        // TODO (Task 9): cast to TabManager and call tabManager.refreshPlayer() in apply/clearAfkEffects
    }

    public void recordActivity(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        lastActivity.put(uuid, now);
        if (settings.detection().patternAlert()) {
            Deque<Long> timestamps = activityTimestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>());
            timestamps.addLast(now);
            if (timestamps.size() > PATTERN_SAMPLE_SIZE) timestamps.pollFirst();
        }
        if (Boolean.TRUE.equals(afkState.get(uuid))) {
            markReturn(player);
        }
    }

    public void onQuit(UUID uuid) {
        lastActivity.remove(uuid);
        afkState.remove(uuid);
        activityTimestamps.remove(uuid);
        BukkitTask t = actionBarTasks.remove(uuid);
        if (t != null) t.cancel();
    }

    @Override
    public boolean isAfk(UUID uuid) {
        return Boolean.TRUE.equals(afkState.get(uuid));
    }

    @Override
    public long getIdleSeconds(UUID uuid) {
        Long last = lastActivity.get(uuid);
        if (last == null) return 0L;
        return (System.currentTimeMillis() - last) / 1000L;
    }

    @Override
    public void setAfk(UUID uuid, boolean afk) {
        Player player = plugin.getServer().getPlayer(uuid);
        if (afk) {
            afkState.put(uuid, true);
            if (player != null) applyAfkEffects(player);
        } else {
            afkState.remove(uuid);
            if (player != null) clearAfkEffects(player);
        }
    }

    // ── Internal scheduler tick ──────────────────────────────────────────────

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            Long last = lastActivity.get(uuid);
            if (last == null) {
                lastActivity.put(uuid, now);
                continue;
            }
            long idleSeconds = (now - last) / 1000L;

            if (!isAfk(uuid) && idleSeconds >= settings.timeout()) {
                markAfk(player);
            } else if (isAfk(uuid) && settings.kickTimeout() > 0 && idleSeconds >= settings.kickTimeout()) {
                player.kick(MM.deserialize(settings.messages().kickReason()));
            }

            if (settings.detection().patternAlert()) {
                checkPattern(player);
            }
        }
    }

    private void markAfk(Player player) {
        UUID uuid = player.getUniqueId();
        afkState.put(uuid, true);
        applyAfkEffects(player);
    }

    private void markReturn(Player player) {
        UUID uuid = player.getUniqueId();
        afkState.remove(uuid);
        clearAfkEffects(player);
    }

    private void applyAfkEffects(Player player) {
        Component broadcast = buildAfkBroadcast(player);
        broadcastFiltered(broadcast);
        startActionBarTask(player);
        // TODO (Task 9): tabManager.refreshPlayer(player)
    }

    private void clearAfkEffects(Player player) {
        UUID uuid = player.getUniqueId();
        Component broadcast = MM.deserialize(
                settings.messages().returnBroadcast(),
                Placeholder.unparsed("player", player.getName()));
        broadcastFiltered(broadcast);
        BukkitTask t = actionBarTasks.remove(uuid);
        if (t != null) t.cancel();
        player.clearTitle();
        // TODO (Task 9): tabManager.refreshPlayer(player)
    }

    private Component buildAfkBroadcast(Player player) {
        if (messageServiceDelegate != null) {
            return messageServiceDelegate.apply(player);
        }
        return MM.deserialize(
                settings.messages().afkBroadcast(),
                Placeholder.unparsed("player", player.getName()));
    }

    private void broadcastFiltered(Component message) {
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            var hp = playerService.getCached(online.getUniqueId());
            boolean muted = hp.map(p -> "true".equals(
                    p.getData("haven-core", "afk-broadcast-muted").orElse(""))).orElse(false);
            if (!muted) online.sendMessage(message);
        }
    }

    private void startActionBarTask(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask old = actionBarTasks.remove(uuid);
        if (old != null) old.cancel();
        Component bar = MM.deserialize(settings.messages().actionBar());
        BukkitTask task = new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline() || !isAfk(uuid)) {
                    cancel();
                    actionBarTasks.remove(uuid);
                    return;
                }
                player.sendActionBar(bar);
            }
        }.runTaskTimer(plugin, 0L, 40L); // every 2 seconds
        actionBarTasks.put(uuid, task);
    }

    private void checkPattern(Player player) {
        UUID uuid = player.getUniqueId();
        Deque<Long> timestamps = activityTimestamps.get(uuid);
        if (timestamps == null || timestamps.size() < PATTERN_SAMPLE_SIZE) return;

        List<Long> list = new ArrayList<>(timestamps);
        List<Long> deltas = new ArrayList<>();
        for (int i = 1; i < list.size(); i++) deltas.add(list.get(i) - list.get(i - 1));

        double mean = deltas.stream().mapToLong(Long::longValue).average().orElse(0);
        double variance = deltas.stream()
                .mapToDouble(d -> (d - mean) * (d - mean))
                .average().orElse(0);
        double stdDev = Math.sqrt(variance);

        if (stdDev < PATTERN_MAX_STD_DEV_MS) {
            String alertPermission = settings.detection().patternAlertPermission();
            Component alert = MM.deserialize(
                    "<yellow>[AFK Alert] <white><player> <yellow>may be using an AFK bypass script (std dev: <std_dev>ms).",
                    Placeholder.unparsed("player", player.getName()),
                    Placeholder.unparsed("std_dev", String.format("%.0f", stdDev)));
            for (Player admin : plugin.getServer().getOnlinePlayers()) {
                if (admin.hasPermission(alertPermission)) admin.sendMessage(alert);
            }
            logger.warning("[AFK] Suspicious pattern detected for " + player.getName()
                    + " (std dev " + String.format("%.0f", stdDev) + "ms)");
            // Reset timestamps so we don't spam alerts
            timestamps.clear();
        }
    }
}
