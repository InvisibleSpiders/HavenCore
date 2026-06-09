package dev.invisiblespiders.haven.core.service;

import dev.invisiblespiders.haven.api.event.HavenEvent;
import dev.invisiblespiders.haven.api.event.HavenPlayerProfileLoadEvent;
import dev.invisiblespiders.haven.api.service.HavenEventBus;
import dev.invisiblespiders.haven.core.repository.PlayerRepository;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;
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
