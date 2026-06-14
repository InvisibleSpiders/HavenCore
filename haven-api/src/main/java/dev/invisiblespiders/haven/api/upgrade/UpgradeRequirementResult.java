package dev.invisiblespiders.haven.api.upgrade;

public record UpgradeRequirementResult(boolean satisfied, String code, String message) {

    public static UpgradeRequirementResult success() {
        return new UpgradeRequirementResult(true, "satisfied", "");
    }

    public static UpgradeRequirementResult failure(String code, String message) {
        return new UpgradeRequirementResult(false, code, message);
    }
}
