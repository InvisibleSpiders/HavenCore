package dev.invisiblespiders.haven.core.upgrade;

import dev.invisiblespiders.haven.api.upgrade.UpgradeContext;
import dev.invisiblespiders.haven.api.upgrade.UpgradeRequirement;
import dev.invisiblespiders.haven.api.upgrade.UpgradeRequirementResult;

import java.util.Objects;

public final class PermissionRequirement implements UpgradeRequirement {

    private final String permission;

    public PermissionRequirement(String permission) {
        this.permission = Objects.requireNonNull(permission, "permission");
        if (permission.isBlank()) {
            throw new IllegalArgumentException("permission cannot be blank");
        }
    }

    @Override
    public String type() {
        return "permission";
    }

    @Override
    public UpgradeRequirementResult validate(UpgradeContext context) {
        return context.purchaser() != null && context.purchaser().hasPermission(permission)
                ? UpgradeRequirementResult.success()
                : UpgradeRequirementResult.failure("missing-permission", "Missing permission.");
    }

    @Override
    public void consume(UpgradeContext context) {
    }

    @Override
    public void refund(UpgradeContext context) {
    }
}
