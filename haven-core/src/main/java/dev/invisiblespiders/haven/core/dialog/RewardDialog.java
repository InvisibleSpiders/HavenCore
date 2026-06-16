package dev.invisiblespiders.haven.core.dialog;

import dev.invisiblespiders.haven.api.reward.HavenRewardService;
import dev.invisiblespiders.haven.api.reward.RewardClaimResult;
import dev.invisiblespiders.haven.api.reward.RewardRecord;
import dev.invisiblespiders.haven.core.config.ConfigManager;
import dev.invisiblespiders.haven.core.text.CoreText;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class RewardDialog {

    private final ConfigManager config;
    private final HavenRewardService rewards;

    public RewardDialog(ConfigManager config, HavenRewardService rewards) {
        this.config = Objects.requireNonNull(config, "config");
        this.rewards = Objects.requireNonNull(rewards, "rewards");
    }

    public void open(Player player) {
        List<RewardRecord> pending = rewards.pending(player.getUniqueId());

        ActionButton close = ActionButton.builder(Component.text("Close"))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        p.closeDialog();
                    }
                }, oneUseOpts()))
                .build();

        List<ActionButton> buttons = pending.isEmpty()
                ? List.of(close)
                : pending.stream().map(reward -> buttonFor(player, reward)).toList();

        List<DialogBody> body = pending.isEmpty()
                ? List.of(DialogBody.plainMessage(
                        CoreText.config(config.getRewards(), "ui.empty", "<gray>No rewards to claim.")))
                : List.of(DialogBody.plainMessage(
                        Component.text(pending.size() + " reward(s) ready", NamedTextColor.GRAY)));

        // multiAction requires non-empty buttons; when pending is empty the close
        // button fills that slot and no separate done button is needed.
        ActionButton done = pending.isEmpty() ? null : close;

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(CoreText.config(config.getRewards(), "ui.title", "<gold>Rewards"))
                        .body(body)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.multiAction(buttons, done, 2)));
        player.showDialog(dialog);
    }

    private ActionButton buttonFor(Player player, RewardRecord reward) {
        String claim = CoreText.configString(config.getRewards(), "ui.claim", "<green>Claim");
        return ActionButton.builder(CoreText.deserialize(claim + " <white>" + reward.displayText()))
                .tooltip(Component.text("#" + reward.id() + " " + reward.providerId() + ":" + reward.rewardType()))
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player p)) {
                        return;
                    }
                    RewardClaimResult result = rewards.claim(p, reward.id());
                    p.sendMessage(messageFor(result));
                    open(p);
                }, multiUseOpts()))
                .build();
    }

    private Component messageFor(RewardClaimResult result) {
        if (result.succeeded()) {
            return CoreText.message(config, "rewards.claimed", "<green>Reward claimed.");
        }
        String key = switch (result.code()) {
            case "reward-expired" -> "rewards.expired";
            case "missing-reward", "reward-unavailable" -> "rewards.unavailable";
            case "provider-unavailable" -> "rewards.unavailable";
            default -> "";
        };
        if (key.isBlank()) {
            return CoreText.deserialize("<red>" + result.message());
        }
        return CoreText.message(config, key, "<red>" + result.message());
    }

    private static ClickCallback.Options oneUseOpts() {
        return ClickCallback.Options.builder()
                .uses(1)
                .lifetime(Duration.ofMinutes(10))
                .build();
    }

    private static ClickCallback.Options multiUseOpts() {
        return ClickCallback.Options.builder()
                .uses(ClickCallback.UNLIMITED_USES)
                .lifetime(Duration.ofMinutes(10))
                .build();
    }
}
