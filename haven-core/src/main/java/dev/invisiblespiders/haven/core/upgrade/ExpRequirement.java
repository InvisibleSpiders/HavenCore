package dev.invisiblespiders.haven.core.upgrade;

import dev.invisiblespiders.haven.api.upgrade.UpgradeContext;
import dev.invisiblespiders.haven.api.upgrade.UpgradeRequirement;
import dev.invisiblespiders.haven.api.upgrade.UpgradeRequirementResult;
import org.bukkit.entity.Player;

public final class ExpRequirement implements UpgradeRequirement {

    private final int levels;

    public ExpRequirement(int levels) {
        if (levels <= 0) {
            throw new IllegalArgumentException("levels must be positive");
        }
        this.levels = levels;
    }

    @Override
    public String type() {
        return "exp";
    }

    @Override
    public UpgradeRequirementResult validate(UpgradeContext context) {
        Player player = requireOnlinePlayer(context);
        return player.getLevel() >= levels
                ? UpgradeRequirementResult.success()
                : UpgradeRequirementResult.failure("insufficient-exp", "Insufficient experience levels.");
    }

    @Override
    public void consume(UpgradeContext context) {
        Player player = requireOnlinePlayer(context);
        if (player.getLevel() < levels) {
            throw new IllegalStateException("experience withdrawal failed");
        }
        player.setLevel(player.getLevel() - levels);
    }

    @Override
    public void refund(UpgradeContext context) {
        Player player = requireOnlinePlayer(context);
        player.setLevel(player.getLevel() + levels);
    }

    private Player requireOnlinePlayer(UpgradeContext context) {
        if (context.purchaser() == null) {
            throw new IllegalStateException("online player is required");
        }
        return context.purchaser();
    }
}
