package dev.invisiblespiders.haven.core.reward;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.invisiblespiders.haven.api.reward.HavenRewardService;
import dev.invisiblespiders.haven.api.reward.RewardRecord;
import dev.invisiblespiders.haven.api.reward.RewardStatus;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import dev.invisiblespiders.haven.core.db.SqlMigrator;
import org.bukkit.Server;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RewardRuntimeBootstrapTest {

    private HikariDataSource dataSource;

    @BeforeEach
    void setup() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite::memory:");
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        SqlMigrator.migrate(dataSource, "haven", "db/migrations/haven", getClass().getClassLoader());
    }

    @AfterEach
    void teardown() {
        dataSource.close();
    }

    @Test
    void registersRewardServiceAndLoginListener() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        ServicesManager services = mock(ServicesManager.class);
        PluginManager plugins = mock(PluginManager.class);
        ConfigManager config = mock(ConfigManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getServicesManager()).thenReturn(services);
        when(server.getPluginManager()).thenReturn(plugins);

        HavenRewardService rewardService = RewardRuntimeBootstrap.register(plugin, dataSource, config);

        ArgumentCaptor<HavenRewardService> serviceCaptor = ArgumentCaptor.forClass(HavenRewardService.class);
        verify(services).register(eq(HavenRewardService.class), serviceCaptor.capture(),
                eq(plugin), eq(ServicePriority.Normal));
        assertSame(rewardService, serviceCaptor.getValue());
        assertInstanceOf(RewardServiceImpl.class, rewardService);

        ArgumentCaptor<Listener> listenerCaptor = ArgumentCaptor.forClass(Listener.class);
        verify(plugins).registerEvents(listenerCaptor.capture(), eq(plugin));
        assertInstanceOf(RewardLoginListener.class, listenerCaptor.getValue());

        RewardRecord record = rewardService.enqueue(UUID.randomUUID(), "test", "crate-key",
                "Crate Key", Map.of("crate", "vote"), Instant.now().plusSeconds(60));
        assertEquals(RewardStatus.PENDING, record.status());
    }
}
