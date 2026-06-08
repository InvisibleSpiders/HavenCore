package dev.invisiblespiders.haven.core.service;

import dev.invisiblespiders.haven.api.event.HavenEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    static class TestEvent extends HavenEvent {
        final String value;
        TestEvent(String value) { this.value = value; }
    }

    static class OtherEvent extends HavenEvent {}

    private EventBusImpl bus;

    @BeforeEach
    void setUp() { bus = new EventBusImpl(); }

    @Test
    void handlerReceivesPublishedEvent() {
        List<String> received = new ArrayList<>();
        bus.subscribe(TestEvent.class, e -> received.add(e.value));
        bus.publish(new TestEvent("hello"));
        assertEquals(List.of("hello"), received);
    }

    @Test
    void unsubscribedHandlerNotCalled() {
        List<String> received = new ArrayList<>();
        dev.invisiblespiders.haven.api.service.HavenEventBus.Handler<TestEvent> handler = e -> received.add(e.value);
        bus.subscribe(TestEvent.class, handler);
        bus.unsubscribe(TestEvent.class, handler);
        bus.publish(new TestEvent("hello"));
        assertTrue(received.isEmpty());
    }

    @Test
    void wrongTypeHandlerNotCalled() {
        List<String> received = new ArrayList<>();
        bus.subscribe(OtherEvent.class, e -> received.add("other"));
        bus.publish(new TestEvent("hello"));
        assertTrue(received.isEmpty());
    }

    @Test
    void multipleHandlersAllCalled() {
        List<String> received = new ArrayList<>();
        bus.subscribe(TestEvent.class, e -> received.add("a:" + e.value));
        bus.subscribe(TestEvent.class, e -> received.add("b:" + e.value));
        bus.publish(new TestEvent("x"));
        assertEquals(List.of("a:x", "b:x"), received);
    }

    @Test
    void failingHandlerDoesNotBlockOthers() {
        List<String> received = new ArrayList<>();
        bus.subscribe(TestEvent.class, e -> { throw new RuntimeException("boom"); });
        bus.subscribe(TestEvent.class, e -> received.add(e.value));
        bus.publish(new TestEvent("ok"));
        assertEquals(List.of("ok"), received);
    }
}
