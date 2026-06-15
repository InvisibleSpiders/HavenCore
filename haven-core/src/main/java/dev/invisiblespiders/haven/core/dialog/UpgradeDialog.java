package dev.invisiblespiders.haven.core.dialog;

import dev.invisiblespiders.haven.api.upgrade.HavenUpgradeService;
import dev.invisiblespiders.haven.api.upgrade.UpgradeDefinition;
import dev.invisiblespiders.haven.api.upgrade.UpgradeLevel;
import dev.invisiblespiders.haven.api.upgrade.UpgradeProvider;
import dev.invisiblespiders.haven.api.upgrade.UpgradePurchaseResult;
import dev.invisiblespiders.haven.api.upgrade.UpgradeViewRequest;
import dev.invisiblespiders.haven.api.upgrade.UpgradeVisibility;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UpgradeDialog {

    private final ConfigManager config;
    private final HavenUpgradeService upgrades;

    public UpgradeDialog(ConfigManager config, HavenUpgradeService upgrades) {
        this.config = Objects.requireNonNull(config, "config");
        this.upgrades = Objects.requireNonNull(upgrades, "upgrades");
    }

    public void open(Player player, UpgradeViewRequest request) {
        List<UpgradeDefinition> definitions = visibleDefinitions(player, request);
        Map<String, UpgradeProvider> providers = upgrades.providers().stream()
                .collect(Collectors.toMap(UpgradeProvider::id, Function.identity(), (a, b) -> a));

        List<ActionButton> buttons = definitions.stream()
                .map(definition -> buttonFor(player, request, definition, providers.get(definition.providerId())))
                .toList();

        List<DialogBody> body = definitions.isEmpty()
                ? List.of(DialogBody.plainMessage(Component.text("No upgrades available.", NamedTextColor.GRAY)))
                : List.of(DialogBody.plainMessage(Component.text(
                        definitions.size() + " upgrade(s) available", NamedTextColor.GRAY)));

        ActionButton close = ActionButton.builder(Component.text("Close"))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        p.closeDialog();
                    }
                }, oneUseOpts()))
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(CoreText.config(config.getUpgrades(), "ui.title", "<gold>Upgrades"))
                        .body(body)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.multiAction(buttons, close, 2)));
        player.showDialog(dialog);
    }

    private List<UpgradeDefinition> visibleDefinitions(Player player, UpgradeViewRequest request) {
        boolean showLocked = config.getUpgrades().getBoolean("ui.show-locked", true);
        boolean showMaxed = config.getUpgrades().getBoolean("ui.show-maxed", true);
        return upgrades.definitions().stream()
                .filter(definition -> request.providerIds().isEmpty()
                        || request.providerIds().contains(definition.providerId()))
                .filter(definition -> request.categoryIds().isEmpty()
                        || request.categoryIds().contains(definition.category().id()))
                .filter(definition -> definition.visibility() != UpgradeVisibility.HIDDEN
                        || player.hasPermission("haven.admin.upgrades"))
                .filter(definition -> definition.visibility() != UpgradeVisibility.LOCKED || showLocked)
                .filter(definition -> !isMaxed(player, definition) || showMaxed)
                .sorted(Comparator
                        .comparingInt((UpgradeDefinition definition) -> definition.category().sortOrder())
                        .thenComparing(definition -> definition.category().displayName())
                        .thenComparing(UpgradeDefinition::id))
                .toList();
    }

    private ActionButton buttonFor(Player player, UpgradeViewRequest request, UpgradeDefinition definition,
                                   UpgradeProvider provider) {
        int currentLevel = upgrades.currentLevel(player.getUniqueId(), definition.id());
        Optional<UpgradeLevel> nextLevel = definition.levels().stream()
                .filter(level -> level.level() == currentLevel + 1)
                .findFirst();
        boolean maxed = nextLevel.isEmpty();
        boolean locked = definition.visibility() == UpgradeVisibility.LOCKED;

        String labelPrefix = maxed
                ? CoreText.configString(config.getUpgrades(), "ui.buttons.maxed", "<gray>Maxed")
                : definition.category().displayName();
        String levelName = nextLevel.map(UpgradeLevel::displayName)
                .orElseGet(() -> definition.levels().isEmpty()
                        ? definition.id()
                        : definition.levels().getLast().displayName());
        Component label = CoreText.deserialize(labelPrefix + " <white>" + levelName);

        String providerName = provider == null ? definition.providerId() : provider.displayName();
        Component tooltip = Component.text(providerName + " / " + definition.id()
                + (locked ? " (locked)" : maxed ? " (maxed)" : ""));

        ActionButton.Builder builder = ActionButton.builder(label).tooltip(tooltip);
        if (!locked && !maxed) {
            builder.action(DialogAction.customClick((view, audience) -> {
                if (!(audience instanceof Player p)) {
                    return;
                }
                openConfirm(p, request, definition, currentLevel, levelName);
            }, multiUseOpts()));
        }
        return builder.build();
    }

    private void openConfirm(Player player, UpgradeViewRequest request, UpgradeDefinition definition,
                             int expectedCurrentLevel, String levelName) {
        ActionButton confirm = ActionButton.builder(
                        CoreText.config(config.getUpgrades(), "ui.buttons.confirm", "<green>Confirm Purchase"))
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player p)) {
                        return;
                    }
                    UpgradePurchaseResult result = purchaseIfLevelUnchanged(p, definition, expectedCurrentLevel);
                    p.sendMessage(messageFor(result));
                    open(p, request);
                }, oneUseOpts()))
                .build();
        ActionButton cancel = ActionButton.builder(
                        CoreText.config(config.getUpgrades(), "ui.buttons.cancel", "<red>Cancel"))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        open(p, request);
                    }
                }, oneUseOpts()))
                .build();
        List<DialogBody> body = List.of(DialogBody.plainMessage(
                Component.text(definition.id() + " / " + levelName, NamedTextColor.GRAY)));
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(
                                CoreText.config(config.getUpgrades(), "ui.confirm-title",
                                        "<gold>Confirm Purchase"))
                        .body(body)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.multiAction(List.of(confirm), cancel, 1)));
        player.showDialog(dialog);
    }

    UpgradePurchaseResult purchaseIfLevelUnchanged(Player player, UpgradeDefinition definition,
                                                   int expectedCurrentLevel) {
        int currentLevel = upgrades.currentLevel(player.getUniqueId(), definition.id());
        if (currentLevel != expectedCurrentLevel) {
            return UpgradePurchaseResult.failure(
                    "stale-upgrade-dialog",
                    "Upgrade state changed. Please try again."
            );
        }
        return upgrades.purchase(player, definition.id(), definition.scope());
    }

    private Component messageFor(UpgradePurchaseResult result) {
        if (result.succeeded()) {
            return CoreText.message(config, "upgrades.purchased", "<green>Upgrade purchased.");
        }
        String key = switch (result.code()) {
            case "unknown-upgrade" -> "upgrades.unknown";
            case "no-permission", "hidden-upgrade", "locked-upgrade" -> "upgrades.no-permission";
            case "requirement-not-met", "requirement-consume-failed" -> "upgrades.cannot-afford";
            case "max-level" -> "upgrades.maxed";
            default -> "";
        };
        if (key.isBlank()) {
            return CoreText.deserialize("<red>" + result.message());
        }
        return CoreText.message(config, key, "<red>" + result.message());
    }

    private boolean isMaxed(Player player, UpgradeDefinition definition) {
        return upgrades.currentLevel(player.getUniqueId(), definition.id()) >= maxLevel(definition);
    }

    private int maxLevel(UpgradeDefinition definition) {
        return definition.levels().stream().mapToInt(UpgradeLevel::level).max().orElse(0);
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
