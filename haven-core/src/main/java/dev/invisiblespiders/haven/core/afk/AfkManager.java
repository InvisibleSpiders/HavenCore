package dev.invisiblespiders.haven.core.afk;

import dev.invisiblespiders.haven.api.service.HavenAfkService;
import dev.invisiblespiders.haven.api.service.HavenPlayerService;
import dev.invisiblespiders.haven.core.tab.TabManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
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

public class AfkManager implements HavenAfkService, Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int PATTERN_SAMPLE_SIZE = 10;
    private static final double PATTERN_MAX_STD_DEV_MS = 200.0;

    private final AfkSettings settings;
    private final Plugin plugin;
    private final HavenPlayerService playerService;
    private final Logger logger;

    // Late-injected after init to avoid circular dependency with TabManager.
    private @Nullable TabManager tabManager;

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

    public void setTabManager(@Nullable TabManager tabManager) {
        this.tabManager = tabManager;
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

    // ── Bukkit event listeners ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!settings.activityEvents().movement()) return;
        if (settings.strictMovement()) {
            if (!event.hasChangedOrientation()) return;
            float yawDelta = Math.abs(event.getTo().getYaw() - event.getFrom().getYaw());
            float pitchDelta = Math.abs(event.getTo().getPitch() - event.getFrom().getPitch());
            if (yawDelta < settings.detection().minRotationDelta()
                    && pitchDelta < settings.detection().minRotationDelta()) return;
        }
        recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInput(PlayerInputEvent event) {
        if (!settings.activityEvents().keyboardInput()) return;
        var input = event.getInput();
        if (input.isForward() || input.isBackward() || input.isLeft() || input.isRight()
                || input.isJump() || input.isSneak() || input.isSprint()) {
            recordActivity(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!settings.activityEvents().chat()) return;
        recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!settings.activityEvents().commands()) return;
        recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!settings.activityEvents().interact()) return;
        recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        var cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || cause == PlayerTeleportEvent.TeleportCause.END_PORTAL
                || cause == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return;
        }
        recordActivity(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        onQuit(event.getPlayer().getUniqueId());
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
        if (tabManager != null) tabManager.refreshPlayer(player);
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
        if (tabManager != null) tabManager.refreshPlayer(player);
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
