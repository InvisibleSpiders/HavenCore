package dev.invisiblespiders.haven.core.suite;

import dev.invisiblespiders.haven.api.service.HavenSuiteEntry;
import dev.invisiblespiders.haven.api.service.HavenSuiteRegistry;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class HavenSuiteRegistryImpl implements HavenSuiteRegistry {

    private final Map<String, HavenSuiteEntry> entries = new ConcurrentHashMap<>();

    @Override
    public void register(HavenSuiteEntry entry) {
        entries.put(entry.pluginName(), entry);
    }

    @Override
    public void unregister(String pluginName) {
        entries.remove(pluginName);
    }

    @Override
    public Collection<HavenSuiteEntry> getAll() {
        return Collections.unmodifiableCollection(entries.values());
    }

    @Override
    public Optional<HavenSuiteEntry> get(String pluginName) {
        return Optional.ofNullable(entries.get(pluginName));
    }
}
