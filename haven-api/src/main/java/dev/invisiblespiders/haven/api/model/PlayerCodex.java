package dev.invisiblespiders.haven.api.model;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerCodex {

    private final UUID playerUuid;
    // entryKey -> discoveredAt timestamp
    private final Map<String, Long> discoveries = new ConcurrentHashMap<>();

    public PlayerCodex(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public UUID getPlayerUuid() { return playerUuid; }

    public boolean hasDiscovered(String entryKey) {
        return discoveries.containsKey(entryKey);
    }

    public long getDiscoveredAt(String entryKey) {
        return discoveries.getOrDefault(entryKey, -1L);
    }

    public Set<String> getDiscoveredKeys() {
        return Collections.unmodifiableSet(discoveries.keySet());
    }

    public int getTotalDiscoveries() {
        return discoveries.size();
    }

    /** Called by the repository on load and by CodexService on new discovery. */
    public void markDiscovered(String entryKey, long timestamp) {
        discoveries.putIfAbsent(entryKey, timestamp);
    }
}
