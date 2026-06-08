package dev.invisiblespiders.haven.core.service;

import dev.invisiblespiders.haven.api.event.HookRegisteredEvent;
import dev.invisiblespiders.haven.api.event.HookUnregisteredEvent;
import dev.invisiblespiders.haven.api.hook.HavenHook;
import dev.invisiblespiders.haven.api.service.HavenEventBus;
import dev.invisiblespiders.haven.api.service.HavenHookRegistry;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class HookRegistryImpl implements HavenHookRegistry {

    private final Map<Class<?>, HavenHook> hooks = new ConcurrentHashMap<>();
    private final HavenEventBus eventBus;
    private final Logger logger;

    public HookRegistryImpl(HavenEventBus eventBus, Logger logger) {
        this.eventBus = eventBus;
        this.logger = logger;
    }

    @Override
    public void register(HavenHook hook) {
        hook.onEnable();
        hooks.put(hook.getClass(), hook);
        if (hook.isAvailable()) {
            logger.info("Hook loaded: " + hook.getId());
        } else {
            logger.info("Hook unavailable (plugin not found): " + hook.getId());
        }
        eventBus.publish(new HookRegisteredEvent(hook));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends HavenHook> Optional<T> get(Class<T> hookType) {
        return Optional.ofNullable((T) hooks.get(hookType));
    }

    @Override
    public boolean isAvailable(Class<? extends HavenHook> hookType) {
        HavenHook hook = hooks.get(hookType);
        return hook != null && hook.isAvailable();
    }

    @Override
    public Collection<HavenHook> getAll() {
        return Collections.unmodifiableCollection(hooks.values());
    }

    public void disableAll() {
        hooks.values().forEach(hook -> {
            try {
                hook.onDisable();
                eventBus.publish(new HookUnregisteredEvent(hook));
            } catch (Exception e) {
                logger.warning("Hook disable error for " + hook.getId() + ": " + e.getMessage());
            }
        });
        hooks.clear();
    }
}
