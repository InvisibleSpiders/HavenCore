package dev.invisiblespiders.haven.api.service;

import dev.invisiblespiders.haven.api.event.HavenEvent;

public interface HavenEventBus {

    @FunctionalInterface
    interface Handler<T extends HavenEvent> {
        void handle(T event);
    }

    <T extends HavenEvent> void subscribe(Class<T> type, Handler<T> handler);

    <T extends HavenEvent> void unsubscribe(Class<T> type, Handler<T> handler);

    <T extends HavenEvent> void publish(T event);
}
