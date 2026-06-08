package dev.invisiblespiders.haven.api.service;

import java.util.UUID;

public interface HavenCooldownService {

    /** Sets a cooldown expiring durationMs milliseconds from now. */
    void set(UUID playerUuid, String key, long durationMs);

    /** Returns remaining milliseconds, or 0 if not active. */
    long remaining(UUID playerUuid, String key);

    boolean isActive(UUID playerUuid, String key);

    void clear(UUID playerUuid, String key);

    /** Clears all cooldowns for the player (called on quit to free memory). */
    void clearAll(UUID playerUuid);
}
