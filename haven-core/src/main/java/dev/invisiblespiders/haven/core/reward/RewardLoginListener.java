package dev.invisiblespiders.haven.core.reward;

import dev.invisiblespiders.haven.api.reward.HavenRewardService;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;

public final class RewardLoginListener implements Listener {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String MESSAGE_KEY = "rewards.login-reminder";
    private static final String FALLBACK_MESSAGE = """
            <gold>You have <yellow>{count}</yellow> unclaimed reward(s). \
            <green><click:run_command:'/rewards'>Click to claim.</click></green>""";

    private final HavenRewardService rewards;
    private final ConfigManager config;

    public RewardLoginListener(HavenRewardService rewards, ConfigManager config) {
        this.rewards = Objects.requireNonNull(rewards, "rewards");
        this.config = config;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        int count = rewards.pending(player.getUniqueId()).size();
        if (count == 0) {
            return;
        }
        String message = configuredMessage().replace("{count}", Integer.toString(count));
        Component component = MINI_MESSAGE.deserialize(message)
                .clickEvent(ClickEvent.runCommand("/rewards"));
        player.sendMessage(component);
    }

    private String configuredMessage() {
        if (config == null) {
            return FALLBACK_MESSAGE;
        }
        try {
            String message = config.getMessage(MESSAGE_KEY);
            if (message != null && !message.isBlank()) {
                return message;
            }
        } catch (RuntimeException ignored) {
            return FALLBACK_MESSAGE;
        }
        return FALLBACK_MESSAGE;
    }
}
