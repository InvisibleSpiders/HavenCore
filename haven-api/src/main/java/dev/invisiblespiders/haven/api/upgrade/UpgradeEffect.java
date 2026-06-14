package dev.invisiblespiders.haven.api.upgrade;

public interface UpgradeEffect {

    String type();

    default UpgradeRequirementResult validate(UpgradeContext context) {
        return UpgradeRequirementResult.success();
    }

    void apply(UpgradeContext context);

    void rollback(UpgradeContext context);
}
