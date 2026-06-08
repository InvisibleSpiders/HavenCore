package dev.invisiblespiders.haven.core.service;

import dev.invisiblespiders.haven.api.service.HavenCooldownService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownServiceImpl implements HavenCooldownService {

    // playerUuid -> (key -> expiryMillis)
    private final Map<UUID, Map<String, Long>> store = new ConcurrentHashMap<>();

    @Override
    public void set(UUID playerUuid, String key, long durationMs) {
        store.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
             .put(key, System.currentTimeMillis() + durationMs);
    }

    @Override
    public long remaining(UUID playerUuid, String key) {
        Map<String, Long> m = store.get(playerUuid);
        if (m == null) return 0L;
        Long expiry = m.get(key);
        if (expiry == null) return 0L;
        return Math.max(0L, expiry - System.currentTimeMillis());
    }

    @Override
    public boolean isActive(UUID playerUuid, String key) {
        return remaining(playerUuid, key) > 0L;
    }

    @Override
    public void clear(UUID playerUuid, String key) {
        Map<String, Long> m = store.get(playerUuid);
        if (m != null) m.remove(key);
    }

    @Override
    public void clearAll(UUID playerUuid) {
        store.remove(playerUuid);
    }
}
