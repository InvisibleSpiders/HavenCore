package dev.invisiblespiders.haven.api.model;

public record ReloadResult(boolean succeeded, String message) {
    public static ReloadResult ok(String message)   { return new ReloadResult(true,  message); }
    public static ReloadResult fail(String message) { return new ReloadResult(false, message); }
}
