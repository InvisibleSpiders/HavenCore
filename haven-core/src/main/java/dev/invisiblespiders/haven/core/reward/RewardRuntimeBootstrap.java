package dev.invisiblespiders.haven.core.reward;

import dev.invisiblespiders.haven.api.reward.HavenRewardService;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import javax.sql.DataSource;
import java.util.Objects;

public final class RewardRuntimeBootstrap {

    private RewardRuntimeBootstrap() {
    }

    public static HavenRewardService register(Plugin plugin, DataSource dataSource, ConfigManager config) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(dataSource, "dataSource");

        RewardRepository repository = new RewardRepository(dataSource);
        RewardServiceImpl rewardService = new RewardServiceImpl(repository);
        plugin.getServer().getServicesManager().register(
                HavenRewardService.class, rewardService, plugin, ServicePriority.Normal);
        plugin.getServer().getPluginManager().registerEvents(
                new RewardLoginListener(rewardService, config), plugin);
        return rewardService;
    }
}
