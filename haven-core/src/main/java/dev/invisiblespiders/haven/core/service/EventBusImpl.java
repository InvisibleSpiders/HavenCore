package dev.invisiblespiders.haven.core.service;

import dev.invisiblespiders.haven.api.event.HavenEvent;
import dev.invisiblespiders.haven.api.service.HavenEventBus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EventBusImpl implements HavenEventBus {

    private final Map<Class<?>, List<Handler<?>>> handlers = new ConcurrentHashMap<>();
    private final Logger logger;

    public EventBusImpl() {
        this(Logger.getLogger(EventBusImpl.class.getName()));
    }

    public EventBusImpl(Logger logger) {
        this.logger = logger;
    }

    @Override
    public <T extends HavenEvent> void subscribe(Class<T> type, Handler<T> handler) {
        handlers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    @Override
    public <T extends HavenEvent> void unsubscribe(Class<T> type, Handler<T> handler) {
        List<Handler<?>> list = handlers.get(type);
        if (list != null) list.remove(handler);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends HavenEvent> void publish(T event) {
        List<Handler<?>> list = handlers.get(event.getClass());
        if (list == null) return;
        for (Handler<?> h : list) {
            try {
                ((Handler<T>) h).handle(event);
            } catch (Exception e) {
                logger.log(Level.WARNING, "EventBus handler failed for " + event.getClass().getName(), e);
            }
        }
    }
}
