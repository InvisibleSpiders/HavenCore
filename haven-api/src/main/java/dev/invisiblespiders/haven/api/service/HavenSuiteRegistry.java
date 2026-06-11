package dev.invisiblespiders.haven.api.service;

import java.util.Collection;
import java.util.Optional;

public interface HavenSuiteRegistry {
    void register(HavenSuiteEntry entry);
    void unregister(String pluginName);
    Collection<HavenSuiteEntry> getAll();
    Optional<HavenSuiteEntry> get(String pluginName);
}
