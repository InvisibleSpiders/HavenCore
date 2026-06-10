package dev.invisiblespiders.haven.core.service;

import dev.invisiblespiders.haven.api.event.HavenEvent;
import dev.invisiblespiders.haven.api.model.HavenPlayer;
import dev.invisiblespiders.haven.api.event.HavenPlayerProfileLoadEvent;
import dev.invisiblespiders.haven.api.service.HavenEventBus;
import dev.invisiblespiders.haven.core.repository.PlayerRepository;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class PlayerServiceImplTest {

    @Test
    void joinPublishesProfileLoadOnScheduledMainThreadContinuation() throws Exception {
        UUID uuid = UUID.randomUUID();
        PlayerRepository repo = mock(PlayerRepository.class);
        when(repo.findByUuid(uuid)).thenReturn(Optional.empty());
        RecordingEventBus eventBus = new RecordingEventBus();
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("InvisibleSpiders");
        when(server.getPlayer(uuid)).thenReturn(player);
        PlayerServiceImpl service = new PlayerServiceImpl(
            repo, eventBus, Runnable::run, plugin, Logger.getLogger(getClass().getName())
        );

        service.onJoin(new PlayerJoinEvent(player, (String) null));

        assertTrue(eventBus.events == 0);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), taskCaptor.capture());
        taskCaptor.getValue().run();

        assertEquals(1, eventBus.events);
        assertTrue(service.getCached(uuid).isPresent());
    }

    @Test
    void quitBeforeProfileLoadCompletesStillPersistsQuitProfile() throws Exception {
        UUID uuid = UUID.randomUUID();
        PlayerRepository repo = mock(PlayerRepository.class);
        HavenPlayer existing = new HavenPlayer(uuid, "OldName", 10L, 20L);
        when(repo.findByUuid(uuid)).thenReturn(Optional.of(existing));
        List<HavenPlayer> saves = new ArrayList<>();
        doAnswer(invocation -> {
            saves.add(invocation.getArgument(0));
            return null;
        }).when(repo).upsert(any(HavenPlayer.class));
        RecordingEventBus eventBus = new RecordingEventBus();
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(scheduler).runTask(eq(plugin), any(Runnable.class));
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("InvisibleSpiders");
        when(server.getPlayer(uuid)).thenReturn(null);
        QueueingExecutor executor = new QueueingExecutor();
        PlayerServiceImpl service = new PlayerServiceImpl(
            repo, eventBus, executor, plugin, Logger.getLogger(getClass().getName())
        );

        service.onJoin(new PlayerJoinEvent(player, (String) null));
        service.onQuit(new PlayerQuitEvent(player, (String) null));
        executor.runAll();

        assertEquals(2, saves.size());
    }

    @Test
    void joinLogsWhenProfileLoadSchedulingFails() throws Exception {
        UUID uuid = UUID.randomUUID();
        PlayerRepository repo = mock(PlayerRepository.class);
        when(repo.findByUuid(uuid)).thenReturn(Optional.empty());
        RecordingEventBus eventBus = new RecordingEventBus();
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(eq(plugin), any(Runnable.class)))
            .thenThrow(new IllegalStateException("Plugin is disabling"));
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("InvisibleSpiders");
        Logger logger = Logger.getLogger(getClass().getName() + "." + uuid);
        logger.setUseParentHandlers(false);
        RecordingLogHandler handler = new RecordingLogHandler();
        logger.addHandler(handler);
        try {
            PlayerServiceImpl service = new PlayerServiceImpl(
                repo, eventBus, Runnable::run, plugin, logger
            );

            service.onJoin(new PlayerJoinEvent(player, (String) null));

            assertTrue(handler.messages.stream().anyMatch(
                message -> message.contains("Failed to schedule profile load for " + uuid)
            ));
        } finally {
            logger.removeHandler(handler);
        }
    }

    private static final class QueueingExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private void runAll() {
            while (!tasks.isEmpty()) {
                tasks.remove().run();
            }
        }
    }

    private static final class RecordingLogHandler extends Handler {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                messages.add(record.getMessage());
            }
        }

        @Override
        public void flush() {}

        @Override
        public void close() throws SecurityException {}
    }

    private static final class RecordingEventBus implements HavenEventBus {
        private int events;

        @Override
        public <T extends HavenEvent> void subscribe(Class<T> type, Handler<T> handler) {}

        @Override
        public <T extends HavenEvent> void unsubscribe(Class<T> type, Handler<T> handler) {}

        @Override
        public <T extends HavenEvent> void publish(T event) {
            if (event instanceof HavenPlayerProfileLoadEvent) {
                events++;
            }
        }
    }
}
