package dev.invisiblespiders.haven.api.hook;

public enum HavenHookStatus {
    AVAILABLE,
    DISABLED,
    MISSING_PLUGIN,
    API_ERROR,
    MISCONFIGURED;

    public boolean isUsable() {
        return this == AVAILABLE;
    }
}
