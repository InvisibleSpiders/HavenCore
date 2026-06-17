package dev.invisiblespiders.haven.api.upgrade;

import net.kyori.adventure.text.Component;

public interface UpgradeRequirement {

    String type();

    UpgradeRequirementResult validate(UpgradeContext context);

    void consume(UpgradeContext context);

    void refund(UpgradeContext context);

    default Component describe() {
        return Component.text(type());
    }
}
