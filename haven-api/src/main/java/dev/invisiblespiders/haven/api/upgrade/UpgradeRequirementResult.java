package dev.invisiblespiders.haven.api.upgrade;

import java.util.Objects;

public record UpgradeRequirementResult(boolean satisfied, String code, String message) {

    public UpgradeRequirementResult {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }

    public static UpgradeRequirementResult success() {
        return new UpgradeRequirementResult(true, "satisfied", "");
    }

    public static UpgradeRequirementResult failure(String code, String message) {
        return new UpgradeRequirementResult(false, code, message);
    }
}
