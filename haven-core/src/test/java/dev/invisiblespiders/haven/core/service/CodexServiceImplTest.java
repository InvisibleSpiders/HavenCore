package dev.invisiblespiders.haven.core.service;

import dev.invisiblespiders.haven.api.event.HavenEvent;
import dev.invisiblespiders.haven.api.exception.HavenCodexServiceException;
import dev.invisiblespiders.haven.api.model.CodexCategory;
import dev.invisiblespiders.haven.api.service.HavenEventBus;
import dev.invisiblespiders.haven.core.repository.CodexRepository;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodexServiceImplTest {

    @Test
    void getCodexWrapsRepositoryFailuresInCodexServiceException() throws SQLException {
        UUID playerUuid = UUID.randomUUID();
        CodexRepository repo = mock(CodexRepository.class);
        when(repo.load(playerUuid)).thenThrow(new SQLException("database unavailable"));
        CodexServiceImpl service = newService(repo);

        CompletionException error = assertThrows(
            CompletionException.class,
            () -> service.getCodex(playerUuid).join()
        );

        assertInstanceOf(HavenCodexServiceException.class, error.getCause());
        assertTrue(error.getCause().getMessage().contains("Failed to load player codex"));
    }

    @Test
    void getDiscoveryCountWrapsRepositoryFailuresInCodexServiceException() throws SQLException {
        UUID playerUuid = UUID.randomUUID();
        CodexRepository repo = mock(CodexRepository.class);
        when(repo.countByCategory(playerUuid, CodexCategory.ITEMS.getId()))
            .thenThrow(new SQLException("database unavailable"));
        CodexServiceImpl service = newService(repo);

        CompletionException error = assertThrows(
            CompletionException.class,
            () -> service.getDiscoveryCount(playerUuid, CodexCategory.ITEMS).join()
        );

        assertInstanceOf(HavenCodexServiceException.class, error.getCause());
        assertTrue(error.getCause().getMessage().contains("Failed to count codex discoveries"));
    }

    private static CodexServiceImpl newService(CodexRepository repo) {
        return new CodexServiceImpl(
            repo,
            new NoOpEventBus(),
            Runnable::run,
            new YamlConfiguration(),
            Logger.getLogger(CodexServiceImplTest.class.getName())
        );
    }

    private static final class NoOpEventBus implements HavenEventBus {
        @Override
        public <T extends HavenEvent> void subscribe(Class<T> type, Handler<T> handler) {}

        @Override
        public <T extends HavenEvent> void unsubscribe(Class<T> type, Handler<T> handler) {}

        @Override
        public <T extends HavenEvent> void publish(T event) {}
    }
}
