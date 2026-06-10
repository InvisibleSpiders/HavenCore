package dev.invisiblespiders.haven.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CooldownServiceTest {

    private CooldownServiceImpl service;
    private final UUID player = UUID.randomUUID();

    @BeforeEach
    void setUp() { service = new CooldownServiceImpl(); }

    @Test
    void notActiveByDefault() {
        assertFalse(service.isActive(player, "test"));
        assertEquals(0, service.remaining(player, "test"));
    }

    @Test
    void activeWithinDuration() {
        service.set(player, "test", 60_000);
        assertTrue(service.isActive(player, "test"));
        assertTrue(service.remaining(player, "test") > 0);
        assertTrue(service.remaining(player, "test") <= 60_000);
    }

    @Test
    void expiredCooldownReturnsZero() throws InterruptedException {
        service.set(player, "test", 1); // 1 ms
        Thread.sleep(10);
        assertFalse(service.isActive(player, "test"));
        assertEquals(0, service.remaining(player, "test"));
    }

    @Test
    void clearRemovesCooldown() {
        service.set(player, "test", 60_000);
        service.clear(player, "test");
        assertFalse(service.isActive(player, "test"));
    }

    @Test
    void clearAllRemovesAllKeys() {
        service.set(player, "a", 60_000);
        service.set(player, "b", 60_000);
        service.clearAll(player);
        assertFalse(service.isActive(player, "a"));
        assertFalse(service.isActive(player, "b"));
    }

    @Test
    void keysAreIsolatedPerPlayer() {
        UUID other = UUID.randomUUID();
        service.set(player, "test", 60_000);
        assertFalse(service.isActive(other, "test"));
    }

    @Test
    void clearLeavesOwnerBucketInPlaceToAvoidConcurrentSetLoss() throws ReflectiveOperationException {
        service.set(player, "test", 60_000);
        service.clear(player, "test");

        assertFalse(service.isActive(player, "test"));
        assertTrue(store().containsKey(player));
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Map<String, Long>> store() throws ReflectiveOperationException {
        Field field = CooldownServiceImpl.class.getDeclaredField("store");
        field.setAccessible(true);
        return (Map<UUID, Map<String, Long>>) field.get(service);
    }
}
