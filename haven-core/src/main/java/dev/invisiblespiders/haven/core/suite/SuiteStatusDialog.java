package dev.invisiblespiders.haven.core.suite;

import dev.invisiblespiders.haven.api.model.ReloadResult;
import dev.invisiblespiders.haven.api.model.SuiteHealthReport;
import dev.invisiblespiders.haven.api.model.SuiteHealthSeverity;
import dev.invisiblespiders.haven.api.service.HavenSuiteEntry;
import dev.invisiblespiders.haven.api.service.HavenSuiteRegistry;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

public final class SuiteStatusDialog {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private SuiteStatusDialog() {}

    public static void show(Player player, HavenSuiteRegistry registry) {
        Collection<HavenSuiteEntry> entries = registry.getAll();

        List<ActionButton> buttons = new ArrayList<>();
        for (HavenSuiteEntry entry : entries) {
            buttons.add(buildPluginButton(entry));
        }
        buttons.add(buildReloadAllButton(registry));

        ActionButton closeBtn = ActionButton.builder(Component.text("Close"))
            .action(DialogAction.customClick((view, audience) -> {
                if (audience instanceof Player p) p.closeDialog();
            }, oneUseOpts()))
            .build();

        List<DialogBody> body = List.of(
            DialogBody.plainMessage(
                Component.text(entries.size() + " plugin(s) registered", NamedTextColor.GRAY))
        );

        Dialog dialog = Dialog.create(factory ->
            factory.empty()
                .base(DialogBase.builder(
                        Component.text("Haven Suite", NamedTextColor.GOLD)
                            .decorate(TextDecoration.BOLD))
                    .body(body)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .build())
                .type(DialogType.multiAction(buttons, closeBtn, 2))
        );
        player.showDialog(dialog);
    }

    private static ActionButton buildPluginButton(HavenSuiteEntry entry) {
        List<SuiteHealthReport> health = entry.health();
        long fails = health.stream().filter(r -> r.severity() == SuiteHealthSeverity.FAIL).count();
        long warns = health.stream().filter(r -> r.severity() == SuiteHealthSeverity.WARN).count();

        NamedTextColor statusColor = fails > 0 ? NamedTextColor.RED
            : warns > 0 ? NamedTextColor.YELLOW
            : NamedTextColor.GREEN;
        String icon = fails > 0 ? "x" : warns > 0 ? "!" : "v";

        Component label = Component.text(
            icon + " " + entry.pluginName() + " v" + entry.pluginVersion(), statusColor);

        Component tooltip = buildHealthTooltip(health);

        return ActionButton.builder(label)
            .tooltip(tooltip)
            .action(DialogAction.customClick(
                (view, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    ReloadResult result = entry.reload();
                    String color = result.succeeded() ? "<green>" : "<red>";
                    p.sendMessage(MM.deserialize(color + result.message()));
                },
                multiUseOpts()
            ))
            .build();
    }

    private static Component buildHealthTooltip(List<SuiteHealthReport> health) {
        if (health.isEmpty()) {
            return Component.text("No health checks reported.", NamedTextColor.GRAY);
        }
        List<Component> lines = new ArrayList<>();
        for (SuiteHealthReport r : health) {
            NamedTextColor c = switch (r.severity()) {
                case PASS -> NamedTextColor.GREEN;
                case WARN -> NamedTextColor.YELLOW;
                case FAIL -> NamedTextColor.RED;
            };
            lines.add(Component.text(r.check() + ": " + r.message(), c));
        }
        Component tooltip = lines.get(0);
        for (int i = 1; i < lines.size(); i++) {
            tooltip = tooltip.append(Component.newline()).append(lines.get(i));
        }
        return tooltip;
    }

    private static ActionButton buildReloadAllButton(HavenSuiteRegistry registry) {
        return ActionButton.builder(Component.text("Reload All", NamedTextColor.AQUA))
            .action(DialogAction.customClick(
                (view, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    int ok = 0, fail = 0;
                    for (HavenSuiteEntry entry : registry.getAll()) {
                        ReloadResult r = entry.reload();
                        if (r.succeeded()) ok++; else fail++;
                    }
                    p.sendMessage(MM.deserialize(
                        "<green>" + ok + " reloaded"
                        + (fail > 0 ? " <red>" + fail + " failed" : "") + "."));
                },
                multiUseOpts()
            ))
            .build();
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
