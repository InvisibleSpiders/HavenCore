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
        CompletableFuture.runAsync(() -> {
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
                if (plugin.getServer().getPlayer(uuid) != null) {
                    cache.put(uuid, player);
                    eventBus.publish(new HavenPlayerProfileLoadEvent(player, firstJoin));
                    if (firstJoin) eventBus.publish(new HavenPlayerFirstJoinEvent(player));
                }
            } catch (Exception e) {
                logger.warning("Failed to load profile for " + name + ": " + e.getMessage());
            }
        }, asyncExecutor);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        HavenPlayer player = cache.remove(uuid);
        if (player != null) {
            player.setLastSeen(System.currentTimeMillis());
            CompletableFuture.runAsync(() -> {
                try { repo.upsert(player); }
                catch (Exception e) { logger.warning("Failed to save profile on quit for " + uuid + ": " + e.getMessage()); }
            }, asyncExecutor);
        }
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
}
