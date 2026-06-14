package dev.invisiblespiders.haven.api.upgrade;

public record UpgradePurchaseResult(boolean succeeded, String code, String message) {

    public static UpgradePurchaseResult success(String message) {
        return new UpgradePurchaseResult(true, "success", message);
    }

    public static UpgradePurchaseResult failure(String code, String message) {
        return new UpgradePurchaseResult(false, code, message);
    }
}
