package dev.invisiblespiders.haven.api.upgrade;

public interface UpgradeRequirement {

    String type();

    UpgradeRequirementResult validate(UpgradeContext context);

    void consume(UpgradeContext context);

    void refund(UpgradeContext context);
}
