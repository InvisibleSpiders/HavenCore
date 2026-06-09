package dev.invisiblespiders.haven.core.service;

import dev.invisiblespiders.haven.api.event.HavenPlayerFirstJoinEvent;
import dev.invisiblespiders.haven.api.event.HavenPlayerProfileLoadEvent;
import dev.invisiblespiders.haven.api.model.HavenPlayer;
import dev.invisiblespiders.haven.api.service.HavenEventBus;
import dev.invisiblespiders.haven.api.service.HavenPlayerService;
import dev.invisiblespiders.haven.core.repository.PlayerRepository;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

public class PlayerServiceImpl implements HavenPlayerService, Listener {

    private final PlayerRepository repo;
    private final HavenEventBus eventBus;
    private final Executor asyncExecutor;
    private final Plugin plugin;
    private final Logger logger;

    private final Map<UUID, HavenPlayer> cache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<LoadedProfile>> loadingProfiles = new ConcurrentHashMap<>();

    public PlayerServiceImpl(PlayerRepository repo, HavenEventBus eventBus,
                             Executor asyncExecutor, Plugin plugin, Logger logger) {
        this.repo = repo;
        this.eventBus = eventBus;
        this.asyncExecutor = asyncExecutor;
        this.plugin = plugin;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();
        CompletableFuture<LoadedProfile> loadFuture = CompletableFuture.supplyAsync(() -> {
            try {
                Optional<HavenPlayer> existing = repo.findByUuid(uuid);
                boolean firstJoin = existing.isEmpty();
                HavenPlayer player;
                if (firstJoin) {
                    long now = System.currentTimeMillis();
                    player = new HavenPlayer(uuid, name, now, now);
                } else {
                    player = existing.get();
                    player.setNameCache(name);
                    player.setLastSeen(System.currentTimeMillis());
                }
                repo.upsert(player);
                return new LoadedProfile(player, firstJoin);
            } catch (Exception e) {
                logger.warning("Failed to load profile for " + name + ": " + e.getMessage());
                return null;
            }
        }, asyncExecutor);
        loadingProfiles.put(uuid, loadFuture);
        loadFuture.thenAccept(loaded -> {
            if (loaded == null) {
                loadingProfiles.remove(uuid, loadFuture);
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    publishLoadedProfile(uuid, loaded);
                } finally {
                    loadingProfiles.remove(uuid, loadFuture);
                }
            });
        }).exceptionally(ex -> {
            loadingProfiles.remove(uuid, loadFuture);
            logger.warning("Failed to schedule profile load for " + uuid + ": " + rootCause(ex).getMessage());
            return null;
        });
    }

    private void publishLoadedProfile(UUID uuid, LoadedProfile loaded) {
        if (plugin.getServer().getPlayer(uuid) == null) {
            return;
        }
        cache.put(uuid, loaded.player());
        eventBus.publish(new HavenPlayerProfileLoadEvent(loaded.player(), loaded.firstJoin()));
        if (loaded.firstJoin()) eventBus.publish(new HavenPlayerFirstJoinEvent(loaded.player()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        HavenPlayer player = cache.remove(uuid);
        if (player != null) {
            saveProfileOnQuit(uuid, player);
            return;
        }
        CompletableFuture<LoadedProfile> loading = loadingProfiles.get(uuid);
        if (loading != null) {
            loading.thenAcceptAsync(loaded -> {
                if (loaded != null) {
                    saveLoadedProfileOnQuit(uuid, loaded);
                }
            }, asyncExecutor).exceptionally(ex -> {
                logger.warning("Failed to save loading profile on quit for " + uuid + ": " + rootCause(ex).getMessage());
                return null;
            });
        }
    }

    private void saveLoadedProfileOnQuit(UUID uuid, LoadedProfile loaded) {
        saveProfileOnQuit(uuid, loaded.player());
    }

    private void saveProfileOnQuit(UUID uuid, HavenPlayer player) {
        player.setLastSeen(System.currentTimeMillis());
        CompletableFuture.runAsync(() -> {
            try { repo.upsert(player); }
            catch (Exception e) { logger.warning("Failed to save profile on quit for " + uuid + ": " + e.getMessage()); }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Optional<HavenPlayer>> get(UUID uuid) {
        HavenPlayer cached = cache.get(uuid);
        if (cached != null) return CompletableFuture.completedFuture(Optional.of(cached));
        return CompletableFuture.supplyAsync(() -> {
            try { return repo.findByUuid(uuid); }
            catch (Exception e) { throw new RuntimeException(e); }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Optional<HavenPlayer>> get(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try { return repo.findByName(name); }
            catch (Exception e) { throw new RuntimeException(e); }
        }, asyncExecutor);
    }

    @Override
    public Collection<HavenPlayer> getOnline() {
        return Collections.unmodifiableCollection(cache.values());
    }

    @Override
    public Optional<HavenPlayer> getCached(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    @Override
    public CompletableFuture<Void> save(HavenPlayer player) {
        return CompletableFuture.runAsync(() -> {
            try { repo.upsert(player); }
            catch (Exception e) { throw new RuntimeException(e); }
        }, asyncExecutor);
    }

    private record LoadedProfile(HavenPlayer player, boolean firstJoin) {}

    private static Throwable rootCause(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }
}
