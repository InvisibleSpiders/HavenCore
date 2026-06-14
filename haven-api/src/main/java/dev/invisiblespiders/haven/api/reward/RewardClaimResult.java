package dev.invisiblespiders.haven.api.reward;

public record RewardClaimResult(boolean succeeded, String code, String message) {

    public static RewardClaimResult success(String message) {
        return new RewardClaimResult(true, "success", message);
    }

    public static RewardClaimResult failure(String code, String message) {
        return new RewardClaimResult(false, code, message);
    }
}
