package dev.invisiblespiders.haven.api.model;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HavenPlayer {

    private final UUID uuid;
    private volatile String nameCache;
    private final long firstSeen;
    private volatile long lastSeen;

    // pluginId -> key -> value; namespace isolation per plugin
    private final Map<String, Map<String, String>> pluginData = new ConcurrentHashMap<>();

    public HavenPlayer(UUID uuid, String nameCache, long firstSeen, long lastSeen) {
        this.uuid = uuid;
        this.nameCache = nameCache;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
    }

    public UUID getUuid() { return uuid; }
    public String getNameCache() { return nameCache; }
    public long getFirstSeen() { return firstSeen; }
    public long getLastSeen() { return lastSeen; }

    public void setNameCache(String name) { this.nameCache = name; }
    public void setLastSeen(long ts) { this.lastSeen = ts; }

    public Optional<String> getData(String pluginId, String key) {
        Map<String, String> map = pluginData.get(pluginId);
        return map == null ? Optional.empty() : Optional.ofNullable(map.get(key));
    }

    public void setData(String pluginId, String key, String value) {
        pluginData.computeIfAbsent(pluginId, k -> new ConcurrentHashMap<>()).put(key, value);
    }

    public void removeData(String pluginId, String key) {
        Map<String, String> map = pluginData.get(pluginId);
        if (map != null) map.remove(key);
    }

    /** Returns a snapshot of all data for the given plugin. */
    public Map<String, String> getDataSnapshot(String pluginId) {
        Map<String, String> map = pluginData.get(pluginId);
        return map == null ? Map.of() : Map.copyOf(map);
    }

    /** Bulk-loads plugin data. Used by the repository on profile load. */
    public void loadData(String pluginId, Map<String, String> data) {
        pluginData.put(pluginId, new ConcurrentHashMap<>(data));
    }
}
