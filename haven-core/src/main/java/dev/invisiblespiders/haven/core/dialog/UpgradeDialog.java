package dev.invisiblespiders.haven.core.dialog;

import dev.invisiblespiders.haven.api.upgrade.HavenUpgradeService;
import dev.invisiblespiders.haven.api.upgrade.UpgradeCategory;
import dev.invisiblespiders.haven.api.upgrade.UpgradeDefinition;
import dev.invisiblespiders.haven.api.upgrade.UpgradeLevel;
import dev.invisiblespiders.haven.api.upgrade.UpgradeProvider;
import dev.invisiblespiders.haven.api.upgrade.UpgradePurchaseResult;
import dev.invisiblespiders.haven.api.upgrade.UpgradeRequirement;
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
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

        boolean showHeaders = config.getUpgrades().getBoolean("ui.layout.show-headers", true);

        List<DialogBody> body = new ArrayList<>();
        if (definitions.isEmpty()) {
            body.add(DialogBody.plainMessage(Component.text("No upgrades available.", NamedTextColor.GRAY)));
        } else {
            body.add(DialogBody.plainMessage(Component.text(
                    definitions.size() + " upgrade(s) available", NamedTextColor.GRAY)));
            body.addAll(upgradeBodyRows(player, request, definitions, providers, showHeaders));
        }

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
                .type(DialogType.multiAction(List.of(close), null, 1)));
        player.showDialog(dialog);
    }

    private List<DialogBody> upgradeBodyRows(
            Player player,
            UpgradeViewRequest request,
            List<UpgradeDefinition> definitions,
            Map<String, UpgradeProvider> providers,
            boolean showHeaders
    ) {
        List<DialogBody> rows = new ArrayList<>();
        List<Component> pendingUpgradeLinks = new ArrayList<>();
        for (BodyLayoutEntry entry : bodyLayoutEntries(definitions, showHeaders)) {
            if (entry.header()) {
                flushUpgradeLinks(rows, pendingUpgradeLinks);
                rows.add(DialogBody.plainMessage(categoryHeader(entry.category())));
                continue;
            }
            pendingUpgradeLinks.add(upgradeLink(
                    player,
                    request,
                    entry.definition(),
                    providers.get(entry.definition().providerId())));
            if (pendingUpgradeLinks.size() == 2) {
                flushUpgradeLinks(rows, pendingUpgradeLinks);
            }
        }
        flushUpgradeLinks(rows, pendingUpgradeLinks);
        return rows;
    }

    private static void flushUpgradeLinks(List<DialogBody> rows, List<Component> pendingUpgradeLinks) {
        if (pendingUpgradeLinks.isEmpty()) {
            return;
        }
        Component row = Component.empty();
        for (int i = 0; i < pendingUpgradeLinks.size(); i++) {
            if (i > 0) {
                row = row.append(Component.text("  ", NamedTextColor.DARK_GRAY));
            }
            row = row.append(pendingUpgradeLinks.get(i));
        }
        rows.add(DialogBody.plainMessage(row));
        pendingUpgradeLinks.clear();
    }

    static List<BodyLayoutEntry> bodyLayoutEntries(List<UpgradeDefinition> definitions, boolean showHeaders) {
        Map<UpgradeCategory, List<UpgradeDefinition>> grouped = new LinkedHashMap<>();
        definitions.stream()
                .sorted(Comparator
                        .comparingInt((UpgradeDefinition definition) -> definition.category().sortOrder())
                        .thenComparing(definition -> definition.category().displayName())
                        .thenComparing(UpgradeDefinition::id))
                .forEach(definition -> grouped.computeIfAbsent(definition.category(), k -> new ArrayList<>()).add(definition));
        List<BodyLayoutEntry> entries = new ArrayList<>();
        for (Map.Entry<UpgradeCategory, List<UpgradeDefinition>> entry : grouped.entrySet()) {
            if (showHeaders) {
                entries.add(BodyLayoutEntry.header(entry.getKey()));
            }
            entry.getValue().stream()
                    .map(BodyLayoutEntry::upgrade)
                    .forEach(entries::add);
        }
        return entries;
    }

    record BodyLayoutEntry(UpgradeCategory category, UpgradeDefinition definition) {
        static BodyLayoutEntry header(UpgradeCategory category) {
            return new BodyLayoutEntry(Objects.requireNonNull(category, "category"), null);
        }

        static BodyLayoutEntry upgrade(UpgradeDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            return new BodyLayoutEntry(definition.category(), definition);
        }

        boolean header() {
            return definition == null;
        }
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

    private Component upgradeLink(Player player, UpgradeViewRequest request, UpgradeDefinition definition,
                                  UpgradeProvider provider) {
        UpgradeDisplay display = displayFor(player, definition, provider);
        Component link = Component.text("[", NamedTextColor.DARK_GRAY)
                .append(display.label())
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
                .hoverEvent(display.tooltip());
        if (!display.locked() && !display.maxed()) {
            link = link.clickEvent(ClickEvent.callback(audience -> {
                if (audience instanceof Player p) {
                    openConfirm(p, request, definition, display.currentLevel(), display.levelName());
                }
            }, multiUseOpts()));
        }
        return link;
    }

    private UpgradeDisplay displayFor(Player player, UpgradeDefinition definition, UpgradeProvider provider) {
        int currentLevel = upgrades.currentLevel(player.getUniqueId(), definition.id());
        Optional<UpgradeLevel> nextLevel = definition.levels().stream()
                .filter(level -> level.level() == currentLevel + 1)
                .findFirst();
        boolean maxed = nextLevel.isEmpty();
        boolean locked = definition.visibility() == UpgradeVisibility.LOCKED;

        String buttonFormat = config.getUpgrades().getString("ui.layout.button-label");
        if (buttonFormat == null || buttonFormat.isBlank()) {
            buttonFormat = "{category} <white>{level}";
        }
        String labelPrefix = maxed
                ? CoreText.configString(config.getUpgrades(), "ui.buttons.maxed", "<gray>Maxed")
                : definition.category().displayName();
        String levelName = nextLevel.map(UpgradeLevel::displayName)
                .orElseGet(() -> definition.levels().isEmpty()
                        ? definition.id()
                        : definition.levels().getLast().displayName());
        String labelText = buttonFormat
                .replace("{category}", labelPrefix)
                .replace("{level}", levelName);
        Component label = CoreText.deserialize(labelText);

        String providerName = provider == null ? definition.providerId() : provider.displayName();
        String status = locked ? " (locked)" : maxed ? " (maxed)" : "";

        String tooltipFormat = config.getUpgrades().getString("ui.layout.tooltip");
        if (tooltipFormat == null || tooltipFormat.isBlank()) {
            tooltipFormat = "{provider} / {id}{status}";
        }
        String tooltipText = tooltipFormat
                .replace("{provider}", providerName)
                .replace("{id}", definition.id())
                .replace("{status}", status);
        Component tooltip = CoreText.deserialize(tooltipText);

        if (!maxed && nextLevel.isPresent()) {
            List<UpgradeRequirement> requirements = nextLevel.get().requirements();
            if (!requirements.isEmpty()) {
                String costFormat = config.getUpgrades().getString("ui.layout.tooltip-cost");
                if (costFormat == null || costFormat.isBlank()) {
                    costFormat = "\n<gray>Cost: {requirements}";
                }
                Component costPrefix = CoreText.deserialize(costFormat.replace("{requirements}", ""));
                tooltip = tooltip.append(costPrefix);
                for (int i = 0; i < requirements.size(); i++) {
                    if (i > 0) {
                        tooltip = tooltip.append(Component.text(", ", NamedTextColor.GRAY));
                    }
                    tooltip = tooltip.append(requirements.get(i).describe());
                }
            }
        }

        return new UpgradeDisplay(label, tooltip, currentLevel, levelName, locked, maxed);
    }

    private Component categoryHeader(UpgradeCategory category) {
        String headerFormat = config.getUpgrades().getString("ui.layout.category-header");
        if (headerFormat == null || headerFormat.isBlank()) {
            headerFormat = "<dark_gray>---------- <gold><bold>{name} Upgrades</bold> <dark_gray>----------";
        }
        String headerText = headerFormat
                .replace("{icon}", category.icon())
                .replace("{name}", category.displayName());
        return CoreText.deserialize(headerText);
    }

    private record UpgradeDisplay(
            Component label,
            Component tooltip,
            int currentLevel,
            String levelName,
            boolean locked,
            boolean maxed
    ) {}

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
