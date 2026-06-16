package dev.invisiblespiders.haven.core.dialog;

import dev.invisiblespiders.haven.api.model.CodexCategory;
import dev.invisiblespiders.haven.api.model.CodexEntry;
import dev.invisiblespiders.haven.api.model.PlayerCodex;
import dev.invisiblespiders.haven.api.service.HavenCodexService;
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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

public class CodexDialog {

    private final ConfigManager config;
    private final HavenCodexService codexService;

    public CodexDialog(ConfigManager config, HavenCodexService codexService) {
        this.config = Objects.requireNonNull(config, "config");
        this.codexService = Objects.requireNonNull(codexService, "codexService");
    }

    public void open(Player player, PlayerCodex playerCodex) {
        Collection<CodexEntry> allEntries = codexService.getAllEntries();
        FileConfiguration codexCfg = config.getCodex();

        List<DialogBody> bodies = new ArrayList<>();
        bodies.add(DialogBody.plainMessage(CoreText.deserialize(
            "<gray>Total: <white>" + playerCodex.getTotalDiscoveries()
                + "<gray>/" + allEntries.size() + " discovered"
        )));

        for (CodexCategory category : CodexCategory.values()) {
            Collection<CodexEntry> entries = codexService.getEntries(category);
            if (entries.isEmpty()) continue;

            long discovered = entries.stream().filter(e -> playerCodex.hasDiscovered(e.key())).count();
            int total = entries.size();

            String categoryDisplay = CoreText.configString(codexCfg,
                "categories." + category.getId(), category.getId());

            List<Integer> milestones = codexCfg != null
                ? codexCfg.getIntegerList("milestones." + category.getId())
                : List.of();

            OptionalInt nextMilestone = milestones.stream()
                .mapToInt(Integer::intValue)
                .filter(m -> m > discovered)
                .findFirst();

            String milestoneInfo = nextMilestone.isPresent()
                ? " <dark_gray>(next milestone: " + nextMilestone.getAsInt() + ")"
                : milestones.isEmpty() ? "" : " <dark_gray>(all milestones reached)";

            bodies.add(DialogBody.plainMessage(CoreText.deserialize(
                categoryDisplay + " <gray>— <white>" + discovered + "<gray>/" + total + milestoneInfo
            )));
        }

        ActionButton close = ActionButton.builder(Component.text("Close"))
            .action(DialogAction.customClick((view, audience) -> {
                if (audience instanceof Player p) p.closeDialog();
            }, ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build()))
            .build();

        Dialog dialog = Dialog.create(factory -> factory.empty()
            .base(DialogBase.builder(
                    CoreText.config(codexCfg, "ui.title", "<dark_green><bold>Your Codex</bold>"))
                .body(bodies)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build())
            .type(DialogType.multiAction(List.of(close), null, 1)));

        player.showDialog(dialog);
    }
}
