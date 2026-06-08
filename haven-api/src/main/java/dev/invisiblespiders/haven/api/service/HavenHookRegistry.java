package dev.invisiblespiders.haven.api.service;

import dev.invisiblespiders.haven.api.hook.HavenHook;

import java.util.Collection;
import java.util.Optional;

public interface HavenHookRegistry {

    /** Registers a hook. Call from your plugin's onEnable after HavenCore is loaded. */
    void register(HavenHook hook);

    /** Returns the hook if registered and available. */
    <T extends HavenHook> Optional<T> get(Class<T> hookType);

    boolean isAvailable(Class<? extends HavenHook> hookType);

    Collection<HavenHook> getAll();
}
