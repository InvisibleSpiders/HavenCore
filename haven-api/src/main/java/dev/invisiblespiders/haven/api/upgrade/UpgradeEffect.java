package dev.invisiblespiders.haven.api.upgrade;

public interface UpgradeEffect {

    String type();

    void apply(UpgradeContext context);

    void rollback(UpgradeContext context);
}
